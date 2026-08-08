// ============================================================
//  هم‌فیلم — بک‌اند Cloudflare Worker
//  REST API + WebSocket (Durable Objects) + D1
//  پروتکل دقیقاً همان پروتکل سایت هم‌فیلم است
// ============================================================
import { signToken, verifyToken, hashPassword, verifyPassword, uid, roomCode } from './auth';

const json = (data, status = 200, headers = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      ...headers
    }
  });

const err = (message, status = 400) => json({ error: message }, status);

// ---------- کمکی D1 ----------
async function q(env, sql, ...params) {
  const stmt = env.DB.prepare(sql).bind(...params);
  const res = await stmt.run();
  return res;
}
async function all(env, sql, ...params) {
  const stmt = env.DB.prepare(sql).bind(...params);
  const res = await stmt.all();
  return res.results;
}
async function first(env, sql, ...params) {
  const res = await all(env, sql, ...params);
  return res[0] || null;
}

// ============================================================
//  روت‌ها
// ============================================================
async function handleRequest(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;
  const secret = env.JWT_SECRET;

  if (method === 'OPTIONS') return json({ ok: true });

  const body = async () => {
    try { return await request.json(); } catch { return {}; }
  };

  // ---------- WebSocket اتاق‌ها ----------
  if (path.startsWith('/ws/') && method === 'GET') {
    const code = path.split('/')[2];
    if (!code) return err('کد اتاق لازم است');
    const id = env.ROOMS.idFromName(code);
    const stub = env.ROOMS.get(id);
    return stub.fetch(request);
  }

  // ================== عمومی ==================
  if (path === '/api/settings/public' && method === 'GET') {
    const s = {};
    for (const row of await all(env, 'SELECT key, value FROM settings')) s[row.key] = row.value;
    return json({
      registrationRequired: s.registrationRequired === 'true',
      maintenance: s.maintenance === 'true',
      announcement: s.announcement || '',
      announcementActive: s.announcementActive === 'true',
      welcomeMessage: s.welcomeMessage || '',
      subscriptionsEnabled: s.subscriptionsEnabled === 'true'
    });
  }

  if (path === '/api/ads' && method === 'GET') {
    const ads = await all(env, 'SELECT id, title, image_url, target_url FROM ads WHERE active = 1');
    return json({ ads });
  }

  if (path === '/api/faqs' && method === 'GET') {
    const faqs = await all(env, 'SELECT id, question, answer FROM faqs');
    return json({ faqs });
  }

  if (path.startsWith('/api/articles') && method === 'GET') {
    const slug = path.replace('/api/articles', '').replace('/', '');
    if (slug) {
      const article = await first(env, 'SELECT * FROM articles WHERE slug = ?', slug);
      return article ? json(article) : err('مقاله یافت نشد', 404);
    }
    const articles = await all(env, 'SELECT slug, title, created_at FROM articles ORDER BY created_at DESC');
    return json({ articles });
  }

  // ---------- فیلم‌ها ----------
  if (path === '/api/movies' && method === 'GET') {
    const page = Math.max(1, parseInt(url.searchParams.get('page') || '1'));
    const genre = url.searchParams.get('genre');
    const qs = url.searchParams.get('q');
    const per = 24;
    let where = '1=1';
    const params = [];
    if (genre) { where += ' AND genres LIKE ?'; params.push(`%${genre}%`); }
    if (qs) { where += ' AND (title LIKE ? OR title_fa LIKE ? OR title_en LIKE ?)'; params.push(`%${qs}%`, `%${qs}%`, `%${qs}%`); }
    params.push(per, (page - 1) * per);
    const movies = await all(env, `SELECT * FROM movies WHERE ${where} ORDER BY views DESC LIMIT ? OFFSET ?`, ...params);
    return json({ movies: movies.map(mapMovie) });
  }

  if (path === '/api/movies/featured' && method === 'GET') {
    const movies = await all(env, "SELECT * FROM movies WHERE featured = 1 ORDER BY views DESC LIMIT 12");
    return json({ featured: true, movies: movies.map(mapMovie) });
  }

  if (path === '/api/movies/genres' && method === 'GET') {
    const genres = await all(env, 'SELECT name, count FROM genres ORDER BY count DESC');
    return json({ genres });
  }

  if (path.startsWith('/api/movies/') && method === 'GET') {
    const slug = path.replace('/api/movies/', '');
    const movie = await first(env, 'SELECT * FROM movies WHERE slug = ?', slug);
    if (!movie) return err('فیلم یافت نشد', 404);
    await q(env, 'UPDATE movies SET views = views + 1 WHERE slug = ?', slug);
    return json(mapMovie(movie));
  }

  // ---------- پلن‌ها ----------
  if (path === '/api/plans' && method === 'GET') {
    const s = await first(env, "SELECT value FROM settings WHERE key = 'subscriptionsEnabled'");
    const plans = await all(env, 'SELECT * FROM plans ORDER BY price_toman');
    return json({ enabled: s?.value === 'true', plans: plans.map(mapPlan) });
  }

  // ================== احراز هویت ==================
  if (path === '/api/auth/register' && method === 'POST') {
    const { name, email, password } = await body();
    if (!name || !email || !password || password.length < 4) return err('نام، ایمیل و رمز (حداقل ۴ کاراکتر) لازم است');
    const exists = await first(env, 'SELECT id FROM users WHERE email = ?', email.toLowerCase());
    if (exists) return err('این ایمیل قبلاً ثبت شده است', 409);
    const id = uid(24);
    const hash = await hashPassword(password);
    await q(env, 'INSERT INTO users (id, name, email, password_hash, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?)',
      id, name, email.toLowerCase(), hash, '🎬', Date.now());
    const token = await signToken({ sub: id, role: 'user' }, secret);
    return json({ token, user: { id, name, email: email.toLowerCase(), avatar: '🎬' } }, 201);
  }

  if (path === '/api/auth/login' && method === 'POST') {
    const { email, password } = await body();
    const user = await first(env, 'SELECT * FROM users WHERE email = ?', (email || '').toLowerCase());
    if (!user || !(await verifyPassword(password || '', user.password_hash))) return err('ایمیل یا رمز اشتباه است', 401);
    const token = await signToken({ sub: user.id, role: 'user' }, secret);
    return json({ token, user: { id: user.id, name: user.name, email: user.email, avatar: user.avatar } });
  }

  if (path === '/api/auth/me' && method === 'GET') {
    const auth = await currentUser(request, env);
    if (!auth.user) return err('نشست نامعتبر است', 401);
    return json({ token: auth.token, user: auth.user });
  }

  if (path === '/api/auth/logout' && method === 'POST') {
    return json({ ok: true }); // JWT سمت کلاینت پاک می‌شود
  }

  // ================== اتاق‌ها ==================
  if (path === '/api/rooms' && method === 'POST') {
    const { name, videoUrl, password, avatar } = await body();
    const code = roomCode();
    const auth = await currentUser(request, env);
    const id = uid(24);
    const hostName = auth.user?.name || 'مهمان';
    await q(env, 'INSERT INTO rooms (id, code, name, password, host_id, host_name, avatar, video_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
      id, code, name || 'اتاق من', password || '', auth.user?.id || '', hostName, avatar || '🎬', videoUrl || '', Date.now());
    return json({ id, code, name: name || 'اتاق من', hostId: auth.user?.id || '', hostName, videoUrl: videoUrl || '' }, 201);
  }

  if (path.startsWith('/api/rooms/') && method === 'GET') {
    const code = path.replace('/api/rooms/', '').toUpperCase();
    const room = await first(env, 'SELECT * FROM rooms WHERE code = ?', code);
    if (!room) return err('اتاق یافت نشد', 404);
    return json({ id: room.id, code: room.code, name: room.name, hostId: room.host_id, hostName: room.host_name, locked: room.locked === 1, videoUrl: room.video_url, createdAt: room.created_at });
  }

  if (path === '/api/reports' && method === 'POST') {
    const { roomCode: rc, reason } = await body();
    await q(env, 'INSERT INTO reports (id, room_code, reason, user_id, created_at) VALUES (?, ?, ?, ?, ?)',
      uid(20), rc || '', reason || '', '', Date.now());
    return json({ ok: true });
  }

  // ================== تیکت‌ها ==================
  if (path.startsWith('/api/support/tickets') && method === 'GET') {
    const auth = await currentUser(request, env);
    if (!auth.user) return err('ابتدا وارد حساب شوید', 401);
    const tickets = await all(env, 'SELECT id, subject, status, created_at, last_reply_at FROM tickets WHERE user_id = ? ORDER BY last_reply_at DESC', auth.user.id);
    return json({ tickets: tickets.map(t => ({ id: t.id, subject: t.subject, status: t.status, createdAt: t.created_at, lastReplyAt: t.last_reply_at })) });
  }

  if (path === '/api/support/tickets' && method === 'POST') {
    const auth = await currentUser(request, env);
    if (!auth.user) return err('ابتدا وارد حساب شوید', 401);
    const { subject, body: text } = await body();
    if (!subject || !text) return err('موضوع و متن تیکت لازم است');
    const id = uid(20);
    const now = Date.now();
    await q(env, 'INSERT INTO tickets (id, user_id, subject, status, created_at, last_reply_at) VALUES (?, ?, ?, ?, ?, ?)', id, auth.user.id, subject, 'open', now, now);
    await q(env, 'INSERT INTO ticket_replies (id, ticket_id, author, body, created_at) VALUES (?, ?, ?, ?, ?)', uid(20), id, auth.user.name, text, now);
    return json({ id, subject, status: 'open', replies: [{ id: uid(20), author: auth.user.name, body: text, createdAt: now }] }, 201);
  }

  if (path.match(/^\/api\/support\/tickets\/[\w-]+\/reply$/) && method === 'POST') {
    const auth = await currentUser(request, env);
    if (!auth.user) return err('ابتدا وارد حساب شوید', 401);
    const id = path.split('/')[4];
    const { body: text } = await body();
    const ticket = await first(env, 'SELECT * FROM tickets WHERE id = ?', id);
    if (!ticket || ticket.user_id !== auth.user.id) return err('تیکت یافت نشد', 404);
    if (ticket.status !== 'open') return err('این تیکت بسته شده است');
    const rid = uid(20);
    await q(env, 'INSERT INTO ticket_replies (id, ticket_id, author, body, created_at) VALUES (?, ?, ?, ?, ?)', rid, id, auth.user.name, text || '', Date.now());
    await q(env, 'UPDATE tickets SET last_reply_at = ? WHERE id = ?', Date.now(), id);
    const replies = await all(env, 'SELECT id, author, body, created_at FROM ticket_replies WHERE ticket_id = ? ORDER BY created_at', id);
    return json({ id, subject: ticket.subject, status: ticket.status, replies: replies.map(r => ({ id: r.id, author: r.author, body: r.body, createdAt: r.created_at })) });
  }

  if (path.startsWith('/api/support/tickets/') && method === 'GET') {
    const auth = await currentUser(request, env);
    if (!auth.user) return err('ابتدا وارد حساب شوید', 401);
    const id = path.replace('/api/support/tickets/', '');
    const ticket = await first(env, 'SELECT * FROM tickets WHERE id = ?', id);
    if (!ticket || ticket.user_id !== auth.user.id) return err('تیکت یافت نشد', 404);
    const replies = await all(env, 'SELECT id, author, body, created_at FROM ticket_replies WHERE ticket_id = ? ORDER BY created_at', id);
    return json({ id, subject: ticket.subject, status: ticket.status, replies: replies.map(r => ({ id: r.id, author: r.author, body: r.body, createdAt: r.created_at })) });
  }

  // ================== اشتراک ==================
  if (path === '/api/subscriptions/me' && method === 'GET') {
    const auth = await currentUser(request, env);
    if (!auth.user) return json({ active: false });
    const sub = await first(env, "SELECT * FROM subscriptions WHERE user_id = ? AND status = 'active' AND expires_at > ?", auth.user.id, Date.now());
    if (!sub) return json({ active: false });
    const plan = await first(env, 'SELECT name FROM plans WHERE id = ?', sub.plan_id);
    return json({ active: true, planName: plan?.name || '', expiresAt: sub.expires_at });
  }

  if (path === '/api/subscriptions/checkout' && method === 'POST') {
    const { planId } = await body();
    const plan = await first(env, 'SELECT * FROM plans WHERE id = ?', planId);
    if (!plan) return err('پلن یافت نشد', 404);
    // درگاه پرداخت — اینجا آدرس Zarinpal نمونه ساخته می‌شود
    // (توکن مرچنت واقعی را در تنظیمات یا KV بگذار)
    const orderId = uid(24);
    const callback = `${url.origin}/api/payments/verify?order=${orderId}`;
    const paymentUrl = `https://www.zarinpal.com/pg/StartPay/${orderId}?callback=${encodeURIComponent(callback)}`;
    return json({ paymentUrl, orderId });
  }

  // ================== پنل ادمین ==================
  if (path === '/api/admin/login' && method === 'POST') {
    const { key } = await body();
    if (!key || key !== env.ADMIN_KEY) return err('کلید ادمین اشتباه است', 401);
    const token = await signToken({ sub: 'admin', role: 'admin' }, secret, 12 * 3600);
    return json({ token });
  }

  if (path.startsWith('/api/admin')) {
    const auth = await currentUser(request, env);
    if (!auth.payload || auth.payload.role !== 'admin') return err('دسترسی غیرمجاز', 401);
    return handleAdmin(request, env, path, method, body);
  }

  return err('مسیر یافت نشد', 404);
}

// ---------- پنل ادمین ----------
async function handleAdmin(request, env, path, method, body) {
  if (path === '/api/admin/me' && method === 'GET') return json({ user: { id: 'admin', name: 'مدیر' } });

  if (path === '/api/admin/stats' && method === 'GET') {
    const users = await first(env, 'SELECT COUNT(*) c FROM users');
    const movies = await first(env, 'SELECT COUNT(*) c FROM movies');
    const tickets = await first(env, "SELECT COUNT(*) c FROM tickets WHERE status = 'open'");
    const subs = await first(env, "SELECT COUNT(*) c FROM subscriptions WHERE status = 'active'");
    return json({ users: users?.c || 0, movies: movies?.c || 0, openTickets: tickets?.c || 0, activeSubscriptions: subs?.c || 0 });
  }

  if (path === '/api/admin/users' && method === 'GET') {
    const users = await all(env, 'SELECT id, name, email, avatar, created_at FROM users ORDER BY created_at DESC LIMIT 100');
    return json({ users });
  }

  if (path === '/api/admin/movies' && method === 'GET') {
    const movies = await all(env, 'SELECT * FROM movies ORDER BY views DESC LIMIT 200');
    return json({ movies: movies.map(mapMovie) });
  }
  if (path === '/api/admin/movies' && method === 'POST') {
    const m = await body();
    const slug = m.slug || uid(10);
    await q(env, `INSERT INTO movies (slug, title, title_en, title_fa, year, genres, country, language, duration_min, age_rating, imdb_rating, imdb_id, satisfaction, views, description, cover_url, source_url, download_links, trailer_url, featured)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      slug, m.title || '', m.titleEn || '', m.titleFa || '', m.year || 0, JSON.stringify(m.genres || []),
      m.country || '', m.language || '', m.durationMin || null, m.ageRating || '', m.imdbRating || null,
      m.imdbId || '', m.satisfaction || null, 0, m.description || '', m.coverUrl || '', m.sourceUrl || '',
      JSON.stringify(m.downloadLinks || []), m.trailerUrl || '', m.featured ? 1 : 0);
    return json({ ok: true, slug }, 201);
  }

  if (path === '/api/admin/settings' && method === 'GET') {
    const rows = await all(env, 'SELECT key, value FROM settings');
    return json(Object.fromEntries(rows.map(r => [r.key, r.value])));
  }
  if (path === '/api/admin/settings' && method === 'POST') {
    const s = await body();
    for (const [k, v] of Object.entries(s)) {
      await q(env, 'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value', k, String(v));
    }
    return json({ ok: true });
  }

  if (path === '/api/admin/tickets' && method === 'GET') {
    const tickets = await all(env, 'SELECT id, user_id, subject, status, created_at FROM tickets ORDER BY created_at DESC LIMIT 100');
    return json({ tickets });
  }

  if (path === '/api/admin/plans' && method === 'GET') {
    const plans = await all(env, 'SELECT * FROM plans ORDER BY price_toman');
    return json({ plans: plans.map(mapPlan) });
  }
  if (path === '/api/admin/plans' && method === 'POST') {
    const p = await body();
    await q(env, 'INSERT INTO plans (id, name, price_toman, discount_percent, final_price_toman, users_per_room, duration_days, features, is_popular) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
      p.id || uid(10), p.name, p.priceToman || 0, p.discountPercent || 0,
      p.finalPriceToman || p.priceToman || 0, p.usersPerRoom || 2, p.durationDays || 30,
      JSON.stringify(p.features || []), p.isPopular ? 1 : 0);
    return json({ ok: true }, 201);
  }

  if (path === '/api/admin/faqs' && method === 'GET') {
    return json({ faqs: await all(env, 'SELECT * FROM faqs') });
  }
  if (path === '/api/admin/faqs' && method === 'POST') {
    const f = await body();
    await q(env, 'INSERT INTO faqs (id, question, answer) VALUES (?, ?, ?)', uid(16), f.question || '', f.answer || '');
    return json({ ok: true }, 201);
  }

  if (path === '/api/admin/ads' && method === 'GET') {
    return json({ ads: await all(env, 'SELECT * FROM ads') });
  }
  if (path === '/api/admin/ads' && method === 'POST') {
    const a = await body();
    await q(env, 'INSERT INTO ads (id, title, image_url, target_url, active) VALUES (?, ?, ?, ?, ?)', uid(16), a.title || '', a.imageUrl || '', a.targetUrl || '', a.active ? 1 : 0);
    return json({ ok: true }, 201);
  }

  if (path === '/api/admin/bans' && method === 'GET') {
    return json({ bans: [] });
  }
  if (path === '/api/admin/subscriptions' && method === 'GET') {
    const subs = await all(env, 'SELECT * FROM subscriptions ORDER BY created_at DESC LIMIT 100');
    return json({ subscriptions: subs });
  }
  if (path === '/api/admin/subscriptions/grant' && method === 'POST') {
    const { userId, planId, days } = await body();
    await q(env, 'INSERT INTO subscriptions (id, user_id, plan_id, status, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)',
      uid(20), userId, planId, 'active', Date.now() + (days || 30) * 86400000, Date.now());
    return json({ ok: true }, 201);
  }

  return err('مسیر ادمین یافت نشد', 404);
}

// ---------- کاربر جاری ----------
async function currentUser(request, env) {
  const h = request.headers.get('Authorization') || '';
  const token = h.replace(/^Bearer\s+/i, '');
  if (!token) return { payload: null, user: null, token: '' };
  const payload = await verifyToken(token, env.JWT_SECRET);
  if (!payload) return { payload: null, user: null, token };
  if (payload.role === 'admin') return { payload, user: null, token };
  const user = await first(env, 'SELECT id, name, email, avatar FROM users WHERE id = ?', payload.sub);
  return { payload, user, token };
}

function mapMovie(m) {
  return {
    slug: m.slug, title: m.title, titleEn: m.title_en, titleFa: m.title_fa, year: m.year,
    genres: JSON.parse(m.genres || '[]'), country: m.country, language: m.language,
    durationMin: m.duration_min, ageRating: m.age_rating, imdbRating: m.imdb_rating, imdbId: m.imdb_id,
    satisfaction: m.satisfaction, views: m.views, description: m.description,
    coverUrl: m.cover_url, sourceUrl: m.source_url,
    downloadLinks: JSON.parse(m.download_links || '[]'), trailerUrl: m.trailer_url || '',
    featured: m.featured === 1
  };
}
function mapPlan(p) {
  return {
    id: p.id, name: p.name, priceToman: p.price_toman, discountPercent: p.discount_percent,
    finalPriceToman: p.final_price_toman, usersPerRoom: p.users_per_room, durationDays: p.duration_days,
    features: JSON.parse(p.features || '[]'), isPopular: p.is_popular === 1
  };
}

export default {
  async fetch(request, env) {
    try {
      return await handleRequest(request, env);
    } catch (e) {
      return err('خطای سرور: ' + (e?.message || ''), 500);
    }
  }
};
