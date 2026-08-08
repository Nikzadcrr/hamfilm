// ============================================================
//  Durable Object اتاق — مدیریت WebSocket اتاق‌های هم‌فیلم
//  پروتکل: join / leave / peers / chat / reaction / typing /
//          control / system / presence / rename / kick / mute / lock / ping
// ============================================================
export class Room {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.roomCode = state.id.name;
    // وضعیت اتاق (در حافظه DO — با هر درخواست مقداردهی می‌شود)
    this.peers = new Map();   // ws -> { id, name, avatar, isHost, muted }
    this.messages = [];
    this.videoUrl = '';
    this.locked = false;
    this.password = '';
    this.hostId = '';
    this.maxPeers = 50;
  }

  // ---------- درخواست ورود ----------
  async fetch(request) {
    const upgrade = request.headers.get('Upgrade') || '';
    if (upgrade.toLowerCase() !== 'websocket') {
      return new Response('Expected WebSocket', { status: 400 });
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    // خواندن اطلاعات اتاق از D1 (برای password / locked)
    try {
      const res = await this.env.DB.prepare('SELECT * FROM rooms WHERE code = ?')
        .bind(this.roomCode).first();
      if (res) {
        this.password = res.password || '';
        this.locked = res.locked === 1;
        this.videoUrl = res.video_url || '';
        if (!this.hostId) this.hostId = res.host_id || '';
      }
    } catch {}

    // ثبت هم‌رشته‌ای
    this.peers.set(server, { id: '', name: '', avatar: '🎬', isHost: false, muted: false });

    server.addEventListener('message', (ev) => this.onMessage(server, ev.data));
    server.addEventListener('close', () => this.onClose(server));
    server.addEventListener('error', () => this.onClose(server));

    return new Response(null, { status: 101, webSocket: client });
  }

  // ---------- دریافت پیام ----------
  onMessage(ws, raw) {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }
    const me = this.peers.get(ws);
    if (!me) return;

    switch (msg.type) {
      case 'join': {
        // بررسی رمز و قفل
        if (this.locked && this.peers.size > 1) {
          this.sendTo(ws, { type: 'system', text: 'اتاق قفل است' });
          return;
        }
        if (this.password && msg.password !== this.password) {
          this.sendTo(ws, { type: 'join', ok: false, error: 'رمز اتاق اشتباه است' });
          return;
        }
        const isFirst = [...this.peers.values()].every(p => !p.id);
        me.id = genId();
        me.name = String(msg.name || 'مهمان').slice(0, 24);
        me.avatar = String(msg.avatar || '🎬');
        me.isHost = isFirst;
        if (isFirst) this.hostId = me.id;

        this.sendTo(ws, {
          type: 'room',
          code: this.roomCode,
          name: this.roomName || `اتاق ${this.roomCode}`,
          hostId: this.hostId,
          locked: this.locked,
          videoUrl: this.videoUrl
        });
        this.sendTo(ws, { type: 'join', ok: true, id: me.id, isHost: me.isHost });
        this.broadcastPeers();
        this.broadcast({ type: 'system', text: `${me.name} به اتاق پیوست` }, ws);
        break;
      }

      case 'chat': {
        const text = String(msg.text || '').slice(0, 1000);
        if (!text.trim()) return;
        if (me.muted) { this.sendTo(ws, { type: 'system', text: 'در چت سکوت شده‌ای' }); return; }
        const m = {
          id: genId(),
          user: { id: me.id, name: me.name, avatar: me.avatar },
          text: text.slice(0, 500),
          time: Date.now()
        };
        this.messages.push(m);
        if (this.messages.length > 500) this.messages.shift();
        this.broadcast({ type: 'chat', msg: m });
        break;
      }

      case 'reaction': {
        this.broadcast({ type: 'reaction', reaction: String(msg.reaction || '❤️').slice(0, 8), name: me.name });
        break;
      }

      case 'typing': {
        this.broadcast({ type: 'typing', id: me.id, on: !!msg.on }, ws);
        break;
      }

      case 'control': {
        if (msg.mode === 'video' && !me.isHost) return; // فقط میزبان ویدیو عوض می‌کند
        if (msg.mode === 'video' && msg.url) this.videoUrl = msg.url;
        this.broadcast({ type: 'control', mode: msg.mode, time: msg.time || 0, url: msg.url || '', by: me.id });
        break;
      }

      // فایل محلی در حال پخش (از حافظه گوشی) — به همه اعلام می‌شود
      case 'file': {
        this.broadcast({
          type: 'file',
          name: String(msg.name || '').slice(0, 120),
          size: msg.size || 0,
          hash: String(msg.hash || '').slice(0, 64),
          by: me.id,
          byName: me.name
        });
        break;
      }

      case 'rename': {
        me.name = String(msg.name || me.name).slice(0, 24);
        this.broadcastPeers();
        break;
      }

      case 'kick': {
        if (!me.isHost) return;
        for (const [target, peer] of this.peers) {
          if (peer.id === msg.id) {
            this.sendTo(target, { type: 'kicked', you: true });
            this.sendTo(target, { type: 'system', text: 'توسط مدیر اخراج شدی' });
            try { target.close(1000, 'kicked'); } catch {}
            this.peers.delete(target);
          }
        }
        this.broadcastPeers();
        break;
      }

      case 'mute': {
        if (!me.isHost) return;
        for (const peer of this.peers.values()) {
          if (peer.id === msg.id) peer.muted = !!msg.muted;
        }
        this.broadcastPeers();
        break;
      }

      case 'lock': {
        if (!me.isHost) return;
        this.locked = !!msg.locked;
        this.broadcast({ type: 'system', text: this.locked ? 'اتاق قفل شد' : 'قفل اتاق باز شد' });
        break;
      }

      case 'presence': {
        this.broadcast({ type: 'presence', id: me.id, rtt: msg.rtt || 0, hasFile: !!msg.hasFile }, ws);
        break;
      }

      case 'pong':
        break; // heartbeat — اتصال زنده است

      case 'leave':
        this.onClose(ws);
        break;
    }
  }

  onClose(ws) {
    const peer = this.peers.get(ws);
    if (!peer) return;
    this.peers.delete(ws);
    if (peer.id) {
      this.broadcast({ type: 'system', text: `${peer.name} از اتاق خارج شد` });
      // اگر میزبان رفت، نفر بعدی میزبان شود
      if (peer.isHost) {
        const next = [...this.peers.values()].find(p => p.id);
        if (next) {
          next.isHost = true;
          this.hostId = next.id;
          this.broadcast({ type: 'system', text: `${next.name} مدیر اتاق شد` });
        }
      }
      this.broadcastPeers();
    }
    try { ws.close(1000); } catch {}
  }

  broadcastPeers() {
    const list = [...this.peers.values()]
      .filter(p => p.id)
      .map(p => ({ id: p.id, name: p.name, avatar: p.avatar, isHost: p.isHost, muted: p.muted }));
    this.broadcast({ type: 'peers', peers: list });
  }

  sendTo(ws, obj) {
    try { ws.send(JSON.stringify(obj)); } catch {}
  }

  broadcast(obj, except = null) {
    for (const [ws] of this.peers) {
      if (ws !== except) this.sendTo(ws, obj);
    }
  }
}

let idCounter = 0;
function genId() {
  idCounter++;
  return `${Date.now().toString(36)}-${idCounter.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
