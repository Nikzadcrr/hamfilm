// ---------- سرور WebSocket اتاق‌ها (نسخه VPS) ----------
// دقیقاً همان پروتکل Durable Object کلادفلر
import { WebSocketServer } from 'ws';
import { db } from './db.js';

const MAX_MESSAGES = 500;

class Room {
  constructor(code) {
    this.code = code;
    this.peers = new Map();   // ws -> { id, name, avatar, isHost, muted }
    this.messages = [];
    this.videoUrl = '';
    this.password = '';
    this.locked = false;
    this.hostId = '';

    // بارگذاری اطلاعات اتاق از دیتابیس
    const row = db.prepare('SELECT * FROM rooms WHERE code = ?').get(code);
    if (row) {
      this.password = row.password || '';
      this.locked = row.locked === 1;
      this.videoUrl = row.video_url || '';
      this.hostId = row.host_id || '';
    }
  }

  add(ws) {
    this.peers.set(ws, { id: '', name: '', avatar: '🎬', isHost: false, muted: false });

    ws.on('message', (raw) => this.onMessage(ws, raw.toString()));
    ws.on('close', () => this.onClose(ws));
    ws.on('error', () => this.onClose(ws));

    // heartbeat
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
  }

  onMessage(ws, raw) {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }
    const me = this.peers.get(ws);
    if (!me) return;

    switch (msg.type) {
      case 'join': {
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

        this.sendTo(ws, { type: 'room', code: this.code, name: `اتاق ${this.code}`, hostId: this.hostId, locked: this.locked, videoUrl: this.videoUrl });
        this.sendTo(ws, { type: 'join', ok: true, id: me.id, isHost: me.isHost });
        // ارسال پیام‌های اخیر به تازه‌وارد
        if (this.messages.length) this.sendTo(ws, { type: 'history', messages: this.messages.slice(-50) });
        this.broadcastPeers();
        this.broadcast({ type: 'system', text: `${me.name} به اتاق پیوست` }, ws);
        break;
      }

      case 'chat': {
        const text = String(msg.text || '').slice(0, 500);
        if (!text.trim()) return;
        if (me.muted) { this.sendTo(ws, { type: 'system', text: 'در چت سکوت شده‌ای' }); return; }
        const m = { id: genId(), user: { id: me.id, name: me.name, avatar: me.avatar }, text, time: Date.now() };
        this.messages.push(m);
        if (this.messages.length > MAX_MESSAGES) this.messages.shift();
        this.broadcast({ type: 'chat', msg: m });
        break;
      }

      case 'reaction':
        this.broadcast({ type: 'reaction', reaction: String(msg.reaction || '❤️').slice(0, 8), name: me.name });
        break;

      case 'typing':
        this.broadcast({ type: 'typing', id: me.id, on: !!msg.on }, ws);
        break;

      case 'control': {
        if (msg.mode === 'video' && !me.isHost) return;
        if (msg.mode === 'video' && msg.url) this.videoUrl = msg.url;
        this.broadcast({ type: 'control', mode: msg.mode, time: msg.time || 0, url: msg.url || '', by: me.id });
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
            target.close(1000, 'kicked');
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

      case 'presence':
        this.broadcast({ type: 'presence', id: me.id, rtt: msg.rtt || 0, hasFile: !!msg.hasFile }, ws);
        break;

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
    // پاک‌سازی اتاق‌های خالی
    if (this.peers.size === 0) rooms.delete(this.code);
  }

  broadcastPeers() {
    const list = [...this.peers.values()]
      .filter(p => p.id)
      .map(p => ({ id: p.id, name: p.name, avatar: p.avatar, isHost: p.isHost, muted: p.muted }));
    this.broadcast({ type: 'peers', peers: list });
  }

  sendTo(ws, obj) {
    if (ws.readyState === 1) ws.send(JSON.stringify(obj));
  }

  broadcast(obj, except = null) {
    for (const [ws] of this.peers) {
      if (ws !== except) this.sendTo(ws, obj);
    }
  }
}

const rooms = new Map();
let idCounter = 0;
function genId() {
  idCounter++;
  return `${Date.now().toString(36)}-${idCounter.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function attachWebSocket(server) {
  // بدون گزینه path — مسیر را خودمان در connection بررسی می‌کنیم
  const wss = new WebSocketServer({ server });

  wss.on('connection', (ws, req) => {
    // مسیر: /ws/{code}
    const code = (req.url || '').split('/').filter(Boolean)[1]?.toUpperCase() || '';
    if (!code) { ws.close(4000, 'bad-code'); return; }
    const room = rooms.get(code) || (rooms.set(code, new Room(code)), rooms.get(code));
    room.add(ws);
  });

  // heartbeat — حذف اتصال‌های مرده
  const interval = setInterval(() => {
    for (const room of rooms.values()) {
      for (const [ws] of room.peers) {
        if (!ws.isAlive) { room.onClose(ws); continue; }
        ws.isAlive = false;
        ws.ping();
      }
    }
  }, 30000);
  wss.on('close', () => clearInterval(interval));

  return wss;
}
