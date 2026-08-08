// ---------- روتر REST — دقیقاً همان اندپوینت‌های نسخه کلادفلر ----------
import { Router } from 'express';
import { db, getSettings } from './db.js';
import { signToken, verifyToken, hashPassword, verifyPassword, uid, roomCode } from './auth.js';

const r = Router();

const mapMovie = m => ({
  slug: m.slug, title: m.title, titleEn: m.title_en, titleFa: m.title_fa, year: m.year,
  genres: JSON.parse(m.genres || '[]'), country: m.country, language: m.language,
  durationMin: m.duration_min, ageRating: m.age_rating, imdbRating: m.imdb_rating, imdbId: m.imdb_id,
  satisfaction: m.satisfaction, views: m.views, description: m.description,
  coverUrl: m.cover_url, sourceUrl: m.source_url,
  downloadLinks: JSON.parse(m.download_links || '[]'), trailerUrl: m.trailer_url || '',
  featured: m.featured === 1
});
const mapPlan = p => ({
  id: p.id, name: p.name, priceToman: p.price_toman, discountPercent: p.discount_percent,
  finalPriceToman: p.final_price_toman, usersPerRoom: p.users_per_room, durationDays: p.duration_days,
  features: JSON.parse(p.features || '[]'), isPopular: p.is_popular === 1
});

// ---------- کاربر جاری ----------
function currentUser(req) {
  const h = req.headers.authorization || '';
  const token = h.replace(/^Bearer\s+/i, '');
  if (!token) return { payload: null, user: null, token };
  const payload = verifyToken(token);
  if (!payload) return { payload: null, user: null, token };
  if (payload.role === 'admin') return { payload, user: null, token };
  const user = db.prepare('SELECT id, name, email, avatar FROM users WHERE id = ?').get(payload.sub);
  return { payload, user, token };
}

// ================== عمومی ==================
r.get('/settings/public', (req, res) => res.json(getSettings()));
r.get('/ads', (req, res) => res.json({ ads: db.prepare('SELECT id, title, image_url, target_url FROM ads WHERE active = 1').all() }));
r.get('/faqs', (req, res) => res.json({ faqs: db.prepare('SELECT id, question, answer FROM faqs').all() }));
r.get('/articles', (req, res) => res.json({ articles: db.prepare('SELECT slug, title, created_at FROM articles ORDER BY created_at DESC').all() }));
r.get('/articles/:slug', (req, res) => {
  const a = db.prepare('SELECT * FROM articles WHERE slug = ?').get(req.params.slug);
  return a ? res.json(a) : res.status(404).json({ error: 'مقاله یافت نشد' });
});

// ---------- فیلم‌ها ----------
r.get('/movies', (req, res) => {
  const page = Math.max(1, parseInt(req.query.page) || 1);
  const per = 24;
  const where = [];
  const params = [];
  if (req.query.genre) { where.push('genres LIKE ?'); params.push(`%${req.query.genre}%`); }
  if (req.query.q) {
    where.push('(title LIKE ? OR title_fa LIKE ? OR title_en LIKE ?)');
    params.push(`%${req.query.q}%`, `%${req.query.q}%`, `%${req.query.q}%`);
  }
  const w = where.length ? 'WHERE ' + where.join(' AND ') : '';
  params.push(per, (page - 1) * per);
  const movies = db.prepare(`SELECT * FROM movies ${w} ORDER BY views DESC LIMIT ? OFFSET ?`).all(...params);
  res.json({ movies: movies.map(mapMovie) });
});
r.get('/movies/featured', (req, res) => {
  const movies = db.prepare('SELECT * FROM movies WHERE featured = 1 ORDER BY views DESC LIMIT 12').all();
  res.json({ featured: true, movies: movies.map(mapMovie) });
});
r.get('/movies/genres', (req, res) => res.json({ genres: db.prepare('SELECT name, count FROM genres ORDER BY count DESC').all() }));
r.get('/movies/:slug', (req, res) => {
  const m = db.prepare('SELECT * FROM movies WHERE slug = ?').get(req.params.slug);
  if (!m) return res.status(404).json({ error: 'فیلم یافت نشد' });
  db.prepare('UPDATE movies SET views = views + 1 WHERE slug = ?').run(req.params.slug);
  res.json(mapMovie(m));
});

// ---------- پلن‌ها ----------
r.get('/plans', (req, res) => {
  const s = db.prepare("SELECT value FROM settings WHERE key = 'subscriptionsEnabled'").get();
  res.json({ enabled: s?.value === 'true', plans: db.prepare('SELECT * FROM plans ORDER BY price_toman').all().map(mapPlan) });
});

// ================== احراز هویت ==================
r.post('/auth/register', (req, res) => {
  const { name, email, password } = req.body || {};
  if (!name || !email || !password || password.length < 4)
    return res.status(400).json({ error: 'نام، ایمیل و رمز (حداقل ۴ کاراکتر) لازم است' });
  const exists = db.prepare('SELECT id FROM users WHERE email = ?').get(email.toLowerCase());
  if (exists) return res.status(409).json({ error: 'این ایمیل قبلاً ثبت شده است' });
  const id = uid(24);
  db.prepare('INSERT INTO users (id, name, email, password_hash, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, name, email.toLowerCase(), hashPassword(password), '🎬', Date.now());
  res.status(201).json({ token: signToken({ sub: id, role: 'user' }), user: { id, name, email: email.toLowerCase(), avatar: '🎬' } });
});

r.post('/auth/login', (req, res) => {
  const { email, password } = req.body || {};
  const user = db.prepare('SELECT * FROM users WHERE email = ?').get((email || '').toLowerCase());
  if (!user || !verifyPassword(password || '', user.password_hash))
    return res.status(401).json({ error: 'ایمیل یا رمز اشتباه است' });
  res.json({ token: signToken({ sub: user.id, role: 'user' }), user: { id: user.id, name: user.name, email: user.email, avatar: user.avatar } });
});

r.get('/auth/me', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.status(401).json({ error: 'نشست نامعتبر است' });
  res.json({ token: auth.token, user: auth.user });
});

r.post('/auth/logout', (req, res) => res.json({ ok: true }));

// ================== اتاق‌ها ==================
r.post('/rooms', (req, res) => {
  const { name, videoUrl, password, avatar } = req.body || {};
  const auth = currentUser(req);
  const code = roomCode();
  const id = uid(24);
  db.prepare(`INSERT INTO rooms (id, code, name, password, host_id, host_name, avatar, video_url, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(id, code, name || 'اتاق من', password || '', auth.user?.id || '', auth.user?.name || 'مهمان', avatar || '🎬', videoUrl || '', Date.now());
  res.status(201).json({ id, code, name: name || 'اتاق من', hostId: auth.user?.id || '', hostName: auth.user?.name || 'مهمان', videoUrl: videoUrl || '' });
});

r.get('/rooms/:code', (req, res) => {
  const room = db.prepare('SELECT * FROM rooms WHERE code = ?').get(req.params.code.toUpperCase());
  if (!room) return res.status(404).json({ error: 'اتاق یافت نشد' });
  res.json({ id: room.id, code: room.code, name: room.name, hostId: room.host_id, hostName: room.host_name, locked: room.locked === 1, videoUrl: room.video_url, createdAt: room.created_at });
});

r.post('/reports', (req, res) => {
  const { roomCode, reason } = req.body || {};
  db.prepare('INSERT INTO reports (id, room_code, reason, user_id, created_at) VALUES (?, ?, ?, ?, ?)')
    .run(uid(20), roomCode || '', reason || '', '', Date.now());
  res.json({ ok: true });
});

// ================== تیکت‌ها ==================
r.get('/support/tickets', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.status(401).json({ error: 'ابتدا وارد حساب شوید' });
  const tickets = db.prepare('SELECT id, subject, status, created_at, last_reply_at FROM tickets WHERE user_id = ? ORDER BY last_reply_at DESC').all(auth.user.id);
  res.json({ tickets });
});
r.post('/support/tickets', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.status(401).json({ error: 'ابتدا وارد حساب شوید' });
  const { subject, body } = req.body || {};
  if (!subject || !body) return res.status(400).json({ error: 'موضوع و متن تیکت لازم است' });
  const id = uid(20);
  const now = Date.now();
  db.prepare('INSERT INTO tickets (id, user_id, subject, status, created_at, last_reply_at) VALUES (?, ?, ?, ?, ?, ?)').run(id, auth.user.id, subject, 'open', now, now);
  db.prepare('INSERT INTO ticket_replies (id, ticket_id, author, body, created_at) VALUES (?, ?, ?, ?, ?)').run(uid(20), id, auth.user.name, body, now);
  res.status(201).json({ id, subject, status: 'open', replies: [] });
});
r.get('/support/tickets/:id', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.status(401).json({ error: 'ابتدا وارد حساب شوید' });
  const t = db.prepare('SELECT * FROM tickets WHERE id = ?').get(req.params.id);
  if (!t || t.user_id !== auth.user.id) return res.status(404).json({ error: 'تیکت یافت نشد' });
  const replies = db.prepare('SELECT id, author, body, created_at FROM ticket_replies WHERE ticket_id = ? ORDER BY created_at').all(t.id);
  res.json({ id: t.id, subject: t.subject, status: t.status, replies });
});
r.post('/support/tickets/:id/reply', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.status(401).json({ error: 'ابتدا وارد حساب شوید' });
  const t = db.prepare('SELECT * FROM tickets WHERE id = ?').get(req.params.id);
  if (!t || t.user_id !== auth.user.id) return res.status(404).json({ error: 'تیکت یافت نشد' });
  if (t.status !== 'open') return res.status(400).json({ error: 'این تیکت بسته شده است' });
  const { body } = req.body || {};
  db.prepare('INSERT INTO ticket_replies (id, ticket_id, author, body, created_at) VALUES (?, ?, ?, ?, ?)').run(uid(20), t.id, auth.user.name, body || '', Date.now());
  db.prepare('UPDATE tickets SET last_reply_at = ? WHERE id = ?').run(Date.now(), t.id);
  const replies = db.prepare('SELECT id, author, body, created_at FROM ticket_replies WHERE ticket_id = ? ORDER BY created_at').all(t.id);
  res.json({ id: t.id, subject: t.subject, status: t.status, replies });
});

// ================== اشتراک ==================
r.get('/subscriptions/me', (req, res) => {
  const auth = currentUser(req);
  if (!auth.user) return res.json({ active: false });
  const sub = db.prepare("SELECT * FROM subscriptions WHERE user_id = ? AND status = 'active' AND expires_at > ?").get(auth.user.id, Date.now());
  if (!sub) return res.json({ active: false });
  const plan = db.prepare('SELECT name FROM plans WHERE id = ?').get(sub.plan_id);
  res.json({ active: true, planName: plan?.name || '', expiresAt: sub.expires_at });
});
r.post('/subscriptions/checkout', (req, res) => {
  const { planId } = req.body || {};
  const plan = db.prepare('SELECT * FROM plans WHERE id = ?').get(planId);
  if (!plan) return res.status(404).json({ error: 'پلن یافت نشد' });
  const orderId = uid(24);
  // نمونه Zarinpal — توکن مرچنت واقعی را از env بخوان
  const callback = `${req.protocol}://${req.get('host')}/api/payments/verify?order=${orderId}`;
  res.json({ paymentUrl: `https://www.zarinpal.com/pg/StartPay/${orderId}?callback=${encodeURIComponent(callback)}`, orderId });
});

// ================== پنل ادمین ==================
r.post('/admin/login', (req, res) => {
  const { key } = req.body || {};
  if (!key || key !== process.env.ADMIN_KEY) return res.status(401).json({ error: 'کلید ادمین اشتباه است' });
  res.json({ token: signToken({ sub: 'admin', role: 'admin' }, 12 * 3600) });
});

function adminOnly(req, res, next) {
  const auth = currentUser(req);
  if (!auth.payload || auth.payload.role !== 'admin') return res.status(401).json({ error: 'دسترسی غیرمجاز' });
  req.admin = true;
  next();
}

r.get('/admin/me', adminOnly, (req, res) => res.json({ user: { id: 'admin', name: 'مدیر' } }));
r.get('/admin/stats', adminOnly, (req, res) => {
  const c = (sql) => db.prepare(sql).get().c;
  res.json({
    users: c('SELECT COUNT(*) c FROM users'),
    movies: c('SELECT COUNT(*) c FROM movies'),
    openTickets: c("SELECT COUNT(*) c FROM tickets WHERE status = 'open'"),
    activeSubscriptions: c("SELECT COUNT(*) c FROM subscriptions WHERE status = 'active'")
  });
});
r.get('/admin/users', adminOnly, (req, res) => {
  res.json({ users: db.prepare('SELECT id, name, email, avatar, created_at FROM users ORDER BY created_at DESC LIMIT 100').all() });
});
r.get('/admin/movies', adminOnly, (req, res) => {
  res.json({ movies: db.prepare('SELECT * FROM movies ORDER BY views DESC LIMIT 200').all().map(mapMovie) });
});
r.post('/admin/movies', adminOnly, (req, res) => {
  const m = req.body || {};
  const slug = m.slug || uid(10);
  db.prepare(`INSERT INTO movies (slug, title, title_en, title_fa, year, genres, country, language, duration_min, age_rating, imdb_rating, imdb_id, satisfaction, views, description, cover_url, source_url, featured)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(slug, m.title || '', m.titleEn || '', m.titleFa || '', m.year || 0, JSON.stringify(m.genres || []),
      m.country || '', m.language || '', m.durationMin || null, m.ageRating || '', m.imdbRating || null,
      m.imdbId || '', m.satisfaction || null, 0, m.description || '', m.coverUrl || '', m.sourceUrl || '', m.featured ? 1 : 0);
  res.status(201).json({ ok: true, slug });
});
r.get('/admin/settings', adminOnly, (req, res) => {
  res.json(Object.fromEntries(db.prepare('SELECT key, value FROM settings').all().map(x => [x.key, x.value])));
});
r.post('/admin/settings', adminOnly, (req, res) => {
  const upsert = db.prepare('INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value');
  for (const [k, v] of Object.entries(req.body || {})) upsert.run(k, String(v));
  res.json({ ok: true });
});
r.get('/admin/tickets', adminOnly, (req, res) => {
  res.json({ tickets: db.prepare('SELECT id, user_id, subject, status, created_at FROM tickets ORDER BY created_at DESC LIMIT 100').all() });
});
r.get('/admin/plans', adminOnly, (req, res) => {
  res.json({ plans: db.prepare('SELECT * FROM plans ORDER BY price_toman').all().map(mapPlan) });
});
r.post('/admin/plans', adminOnly, (req, res) => {
  const p = req.body || {};
  db.prepare('INSERT INTO plans (id, name, price_toman, discount_percent, final_price_toman, users_per_room, duration_days, features, is_popular) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)')
    .run(p.id || uid(10), p.name, p.priceToman || 0, p.discountPercent || 0, p.finalPriceToman || p.priceToman || 0,
      p.usersPerRoom || 2, p.durationDays || 30, JSON.stringify(p.features || []), p.isPopular ? 1 : 0);
  res.status(201).json({ ok: true });
});
r.get('/admin/faqs', adminOnly, (req, res) => res.json({ faqs: db.prepare('SELECT * FROM faqs').all() }));
r.post('/admin/faqs', adminOnly, (req, res) => {
  const f = req.body || {};
  db.prepare('INSERT INTO faqs (id, question, answer) VALUES (?, ?, ?)').run(uid(16), f.question || '', f.answer || '');
  res.status(201).json({ ok: true });
});
r.get('/admin/ads', adminOnly, (req, res) => res.json({ ads: db.prepare('SELECT * FROM ads').all() }));
r.post('/admin/ads', adminOnly, (req, res) => {
  const a = req.body || {};
  db.prepare('INSERT INTO ads (id, title, image_url, target_url, active) VALUES (?, ?, ?, ?, ?)').run(uid(16), a.title || '', a.imageUrl || '', a.targetUrl || '', a.active ? 1 : 0);
  res.status(201).json({ ok: true });
});
r.get('/admin/bans', adminOnly, (req, res) => res.json({ bans: [] }));
r.get('/admin/subscriptions', adminOnly, (req, res) => {
  res.json({ subscriptions: db.prepare('SELECT * FROM subscriptions ORDER BY created_at DESC LIMIT 100').all() });
});
r.post('/admin/subscriptions/grant', adminOnly, (req, res) => {
  const { userId, planId, days } = req.body || {};
  db.prepare('INSERT INTO subscriptions (id, user_id, plan_id, status, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)')
    .run(uid(20), userId, planId, 'active', Date.now() + (days || 30) * 86400000, Date.now());
  res.status(201).json({ ok: true });
});

export default r;
