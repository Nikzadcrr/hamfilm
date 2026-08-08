// ---------- دیتابیس SQLite ----------
import Database from 'better-sqlite3';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dbPath = process.env.DATABASE_PATH || path.join(__dirname, '..', 'data', 'hamfilm.db');
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

export const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

// ---------- اسکیما (همان schema.sql کلادفلر) ----------
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE,
  password_hash TEXT NOT NULL, avatar TEXT DEFAULT '🎬', created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS rooms (
  id TEXT PRIMARY KEY, code TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
  password TEXT DEFAULT '', host_id TEXT DEFAULT '', host_name TEXT DEFAULT 'مهمان',
  avatar TEXT DEFAULT '🎬', video_url TEXT DEFAULT '', locked INTEGER DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS movies (
  slug TEXT PRIMARY KEY, title TEXT NOT NULL, title_en TEXT DEFAULT '', title_fa TEXT DEFAULT '',
  year INTEGER DEFAULT 0, genres TEXT DEFAULT '[]', country TEXT DEFAULT '', language TEXT DEFAULT '',
  duration_min INTEGER, age_rating TEXT DEFAULT '', imdb_rating REAL, imdb_id TEXT DEFAULT '',
  satisfaction INTEGER, views INTEGER DEFAULT 0, description TEXT DEFAULT '',
  cover_url TEXT DEFAULT '', source_url TEXT DEFAULT '',
  download_links TEXT DEFAULT '[]', trailer_url TEXT DEFAULT '', featured INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS genres (name TEXT PRIMARY KEY, count INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS plans (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, price_toman INTEGER NOT NULL,
  discount_percent INTEGER DEFAULT 0, final_price_toman INTEGER NOT NULL,
  users_per_room INTEGER DEFAULT 2, duration_days INTEGER DEFAULT 30,
  features TEXT DEFAULT '[]', is_popular INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS subscriptions (
  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, plan_id TEXT NOT NULL,
  status TEXT DEFAULT 'active', expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tickets (
  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, subject TEXT NOT NULL,
  status TEXT DEFAULT 'open', created_at INTEGER NOT NULL, last_reply_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS ticket_replies (
  id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL, author TEXT NOT NULL,
  body TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS ads (
  id TEXT PRIMARY KEY, title TEXT DEFAULT '', image_url TEXT DEFAULT '',
  target_url TEXT DEFAULT '', active INTEGER DEFAULT 1
);
CREATE TABLE IF NOT EXISTS faqs (
  id TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS articles (
  slug TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT DEFAULT '', created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT DEFAULT '');
CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY, room_code TEXT DEFAULT '', reason TEXT DEFAULT '',
  user_id TEXT DEFAULT '', created_at INTEGER NOT NULL
);
`);

// ---------- seed اولیه ----------
export function seed() {
  const ins = db.prepare('INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)');
  [
    ['registrationRequired', 'false'],
    ['maintenance', 'false'],
    ['announcement', ''],
    ['announcementActive', 'false'],
    ['welcomeMessage', 'خوش آمدید!'],
    ['subscriptionsEnabled', 'false']
  ].forEach(([k, v]) => ins.run(k, v));

  const insPlan = db.prepare(`INSERT OR IGNORE INTO plans
    (id, name, price_toman, discount_percent, final_price_toman, users_per_room, duration_days, features, is_popular)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`);
  insPlan.run('plan-basic', 'پایه', 50000, 0, 50000, 2, 30, '["ظرفیت ۳ نفر","پشتیبانی"]', 0);
  insPlan.run('plan-pro', 'محبوب', 150000, 10, 135000, 5, 30, '["ظرفیت ۶ نفر","پشتیبانی ویژه","بدون تبلیغ"]', 1);
  insPlan.run('plan-max', 'ویژه', 300000, 20, 240000, 10, 30, '["ظرفیت ۱۱ نفر","پشتیبانی ۲۴/۷","بدون تبلیغ","کیفیت 4K"]', 0);
}
seed();

export function getSettings() {
  const rows = db.prepare('SELECT key, value FROM settings').all();
  const s = Object.fromEntries(rows.map(r => [r.key, r.value]));
  return {
    registrationRequired: s.registrationRequired === 'true',
    maintenance: s.maintenance === 'true',
    announcement: s.announcement || '',
    announcementActive: s.announcementActive === 'true',
    welcomeMessage: s.welcomeMessage || '',
    subscriptionsEnabled: s.subscriptionsEnabled === 'true'
  };
}
