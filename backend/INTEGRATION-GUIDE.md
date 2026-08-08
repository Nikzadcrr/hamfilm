# 🔌 سند یکپارچه‌سازی بک‌اند هم‌فیلم با سایت
### راهنمای کامل برای تیم توسعه (ایجنتسی)

**نسخه:** 1.3 — **تاریخ:** ۸ آگوست ۲۰۲۶
**هدف:** اتصال کامل سورس سایت (فرانت React فعلی) به بک‌اند جدید هم‌فیلم — بدون بازنویسی سرور از صفر.

---

## ۱) نمای کلی معماری

```
┌──────────────────────────────┐
│   فرانت سایت (React/Vite)    │ ← سورس فعلی سایت
│   + اپ اندروید (Kotlin)      │
└──────────┬───────────────────┘
           │ HTTPS/JSON (REST)      │ WebSocket (اتاق‌ها)
           ▼                        ▼
┌──────────────────────┐   ┌──────────────────────┐
│  بک‌اند هم‌فیلم        │   │  اتاق‌ها (بلادرنگ)     │
│  REST API + ادمین     │◄──┤  Cloudflare DO / VPS │
└──────────┬───────────┘   └──────────────────────┘
           ▼
   دیتابیس: D1 (کلادفلر) یا SQLite (VPS)
```

**دو گزینه استقرار (کد یکسان، فقط تنظیمات فرق دارد):**

| | ☁️ Cloudflare Workers | 🖥️ VPS (Node.js) |
|---|---|---|
| پوشه | `backend/cloudflare/` | `backend/vps/` |
| دیتابیس | D1 (سرویس ابری) | SQLite (فایل) |
| اتاق‌ها | Durable Objects | حافظه سرور |
| استقرار | `wrangler deploy` | `docker compose up -d` |

> اپ اندروید و فرانت سایت **هیچ تفاوتی** بین این دو نمی‌بینند — فقط آدرس Base URL عوض می‌شود.

---

## ۲) آدرس‌های پایه

```
REST:  https://{HOST}/api/...
WebSocket: wss://{HOST}/ws/{roomCode}
```

پیشنهاد: `api.hamfilm.ir` روی VPS یا `hamfilm-api.{sub}.workers.dev` روی کلادفلر.

---

## ۳) نقشه کامل REST API ها

### 🔓 عمومی (بدون توکن)

| Method | مسیر | توضیح | پاسخ نمونه |
|---|---|---|---|
| GET | `/api/settings/public` | وضعیت سرویس، اعلامیه، پیام خوش‌آمد | `{registrationRequired, maintenance, announcement, announcementActive, welcomeMessage, subscriptionsEnabled}` |
| GET | `/api/ads` | تبلیغات فعال | `{ads: [...]}` |
| GET | `/api/faqs` | سوالات متداول | `{faqs: [...]}` |
| GET | `/api/articles` | لیست مقالات | `{articles: [...]}` |
| GET | `/api/articles/{slug}` | مقاله | `{slug, title, body, created_at}` |
| GET | `/api/movies?page=1&genre=درام&q=جستجو` | لیست فیلم‌ها (صفحه‌بندی ۲۴تایی) | `{movies: [...]}` |
| GET | `/api/movies/featured` | فیلم‌های ویژه | `{featured: true, movies: [...]}` |
| GET | `/api/movies/genres` | ژانرها با تعداد | `{genres: [{name, count}]}` |
| GET | `/api/movies/{slug}` | جزئیات فیلم (+۱ بازدید) | `{...movie}` |
| GET | `/api/plans` | پلن‌های اشتراک | `{enabled, plans: [...]}` |
| POST | `/api/reports` | گزارش تخلف | `{ok: true}` |

### 🔐 احراز هویت (JWT)

| Method | مسیر | بدنه | پاسخ |
|---|---|---|---|
| POST | `/api/auth/register` | `{name, email, password}` | `{token, user}` (201) |
| POST | `/api/auth/login` | `{email, password}` | `{token, user}` |
| GET | `/api/auth/me` | هدر `Authorization: Bearer {token}` | `{token, user}` |
| POST | `/api/auth/logout` | — | `{ok: true}` |

### 🎟️ تیکت‌ها (نیازمند توکن)

| Method | مسیر | توضیح |
|---|---|---|
| GET | `/api/support/tickets` | تیکت‌های من |
| POST | `/api/support/tickets` | `{subject, body}` |
| GET | `/api/support/tickets/{id}` | جزئیات + پاسخ‌ها |
| POST | `/api/support/tickets/{id}/reply` | `{body}` |

### 💳 اشتراک

| Method | مسیر | توضیح |
|---|---|---|
| GET | `/api/subscriptions/me` | وضعیت اشتراک فعلی |
| POST | `/api/subscriptions/checkout` | `{planId}` → `{paymentUrl, orderId}` (Zarinpal) |

### 🏠 اتاق‌ها

| Method | مسیر | توضیح |
|---|---|---|
| POST | `/api/rooms` | `{name, videoUrl?, password?, avatar?}` → `{id, code, ...}` |
| GET | `/api/rooms/{code}` | اطلاعات اتاق (برای پیش‌نمایش) |

### 🛡️ پنل ادمین (کلید ادمین)

| Method | مسیر | توضیح |
|---|---|---|
| POST | `/api/admin/login` | `{key}` → `{token}` (کلید از env: `ADMIN_KEY`) |
| GET | `/api/admin/me` | پروفایل ادمین |
| GET | `/api/admin/stats` | `{users, movies, openTickets, activeSubscriptions}` |
| GET/POST | `/api/admin/users` | مدیریت کاربران |
| GET/POST | `/api/admin/movies` | مدیریت فیلم‌ها (با `download_links`) |
| GET/POST | `/api/admin/settings` | تنظیمات عمومی (اعلامیه، ثبت‌نام، اشتراک...) |
| GET | `/api/admin/tickets` | همه تیکت‌ها |
| GET/POST | `/api/admin/plans` | مدیریت پلن‌ها |
| GET/POST | `/api/admin/faqs` | مدیریت سوالات |
| GET/POST | `/api/admin/ads` | مدیریت تبلیغات |
| GET | `/api/admin/bans` | لیست مسدودی‌ها |
| GET | `/api/admin/subscriptions` | اشتراک‌ها |
| POST | `/api/admin/subscriptions/grant` | `{userId, planId, days}` — اعطای اشتراک |

> همه مسیرهای ادمین بدون توکن ادمین → `401 {"error":"دسترسی غیرمجاز"}`

---

## ۴) مدل داده فیلم (برای پنل ادمین)

```json
{
  "slug": "movie-slug",
  "title": "Movie Title",
  "titleEn": "Movie Title EN",
  "titleFa": "نام فارسی",
  "year": 2026,
  "genres": ["درام", "اکشن"],
  "country": "USA",
  "language": "English",
  "durationMin": 120,
  "ageRating": "PG-13",
  "imdbRating": 8.5,
  "imdbId": "tt1234567",
  "satisfaction": 82,
  "views": 1500,
  "description": "توضیح کامل...",
  "coverUrl": "https://.../cover.jpg",
  "sourceUrl": "https://.../watch",
  "downloadLinks": [
    { "label": "کیفیت 1080p", "url": "https://.../movie-1080.mp4", "quality": "1080p", "size": "1.4GB" },
    { "label": "کیفیت 720p", "url": "https://.../movie-720.mp4", "quality": "720p", "size": "800MB" }
  ],
  "trailerUrl": "https://.../trailer.mp4",
  "featured": true
}
```

---

## ۵) پروتکل WebSocket اتاق‌ها (مهم‌ترین بخش)

**آدرس:** `wss://{HOST}/ws/{CODE}` — ورود با پیام JSON.

### پیام‌های کلاینت → سرور

| type | بدنه | توضیح |
|---|---|---|
| `join` | `{name, avatar, password?}` | ورود (اولین نفر = میزبان) |
| `chat` | `{text}` | پیام چت (حداکثر ۵۰۰ کاراکتر) |
| `reaction` | `{reaction: "🔥"}` | واکنش لحظه‌ای |
| `typing` | `{on: true/false}` | اندیکاتور تایپینگ |
| `control` | `{mode: "play"/"pause"/"seek"/"video", time?, url?}` | همگام‌سازی پخش (تغییر ویدیو فقط میزبان) |
| `file` | `{name, size, hash?}` | اعلام فایل محلی در حال پخش |
| `rename` | `{name}` | تغییر نام نمایشی |
| `kick` | `{id}` | اخراج (فقط میزبان) |
| `mute` | `{id, muted}` | سکوت در چت (فقط میزبان) |
| `lock` | `{locked}` | قفل/باز کردن اتاق (فقط میزبان) |
| `presence` | `{rtt, hasFile}` | وضعیت برای همگام‌سازی فایل |
| `leave` | — | خروج |

### پیام‌های سرور → کلاینت

| type | محتوا | توضیح |
|---|---|---|
| `room` | `{code, name, hostId, locked, videoUrl}` | اطلاعات اولیه اتاق |
| `join` | `{ok, id, isHost}` | نتیجه ورود |
| `peers` | `{peers: [{id, name, avatar, isHost, muted}]}` | لیست زنده اعضا |
| `chat` | `{msg: {id, user, text, time}}` | پیام جدید |
| `reaction` | `{reaction, name}` | واکنش |
| `typing` | `{id, on}` | تایپینگ |
| `control` | `{mode, time, url, by}` | دستور پخش همگام |
| `file` | `{name, size, hash, by, byName}` | فایل محلی اعلام‌شده |
| `system` | `{text}` | پیام سیستمی (ورود/خروج/قفل...) |
| `kicked` | `{you: true}` | اخراج شدن |
| `history` | `{messages: [...]}` | ۵۰ پیام اخیر (فقط VPS) |

### نمونه کد جاوااسکریپت (برای فرانت سایت)

```js
// اتصال به اتاق
const ws = new WebSocket(`wss://${location.host}/ws/${roomCode}`);

ws.onopen = () => {
  ws.send(JSON.stringify({ type: 'join', name: 'علی', avatar: '🎬' }));
};

// ارسال پیام چت
function sendChat(text) {
  ws.send(JSON.stringify({ type: 'chat', text }));
}

// همگام‌سازی پخش
function syncPlayback(mode, time) {
  ws.send(JSON.stringify({ type: 'control', mode, time }));
}

// دریافت
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data);
  switch (msg.type) {
    case 'peers': updateUsers(msg.peers); break;
    case 'chat': appendMessage(msg.msg); break;
    case 'reaction': showReaction(msg.reaction); break;
    case 'control': handleControl(msg); break;
    case 'system': showSystem(msg.text); break;
  }
};
```

---

## ۶) راهنمای اتصال فرانت React فعلی (قدم‌به‌قدم)

1. **Base URL:** در فایل تنظیمات سایت، آدرس API را به `https://{HOST}/api` تغییر دهید (یا در فایل env: `VITE_API_BASE`).
2. **جایگزینی fetch ها:** همه فراخوانی‌های `fetch` فعلی به مسیرهای همین سند تغییر کند. ساختار پاسخ‌ها یکسان است:
   - موفق: `200` با بدنه JSON
   - خطا: `{error: "پیام فارسی"}`
3. **توکن:** هدر `Authorization: Bearer {token}` را به درخواست‌های نیازمند احراز اضافه کنید. توکن را در `localStorage` (مثل قبل) یا کوکی httpOnly نگه دارید.
4. **WebSocket:** بخش اتاق را با نمونه کد بالا جایگزین کنید. پروتکل پیام‌ها **دقیقاً همانی است که سایت الان استفاده می‌کند** (`join`, `chat`, `control`, ...) — فقط آدرس به بک‌اند جدید می‌خورد.
5. **اتاق جدید:** `POST /api/rooms` → کد اتاق برمی‌گردد → با آن به WebSocket وصل شوید.
6. **پنل ادمین:** صفحه `/admin` را به `POST /api/admin/login` (با کلید) وصل کنید؛ سپس بقیه endpoint های ادمین با همان توکن.

### چک‌لیست اتصال

- [ ] تنظیمات عمومی در صفحه اصلی سایت نمایش داده می‌شود (`/api/settings/public`)
- [ ] لیست فیلم‌ها و جزئیات از بک‌اند جدید می‌آید
- [ ] ورود/ثبت‌نام کار می‌کند و نشست حفظ می‌شود
- [ ] ساخت اتاق → اتصال WebSocket → چت و همگام‌سازی پخش
- [ ] واکنش‌ها، تایپینگ، حضور اعضا
- [ ] میزبان می‌تواند kick/mute/lock کند
- [ ] تیکت‌ها و پلن‌ها
- [ ] پنل ادمین با کلید (`ADMIN_KEY`) باز می‌شود

---

## ۷) راه‌اندازی (برای تیم DevOps)

### ☁️ کلادفلر
```bash
npm i -g wrangler
cd backend/cloudflare
wrangler d1 create hamfilm                # ID را در wrangler.toml بگذار
wrangler d1 execute hamfilm --remote --file=schema.sql
wrangler secret put JWT_SECRET            # رشته تصادفی بلند
wrangler secret put ADMIN_KEY             # کلید پنل ادمین
wrangler deploy
```

### 🖥️ VPS
```bash
cd backend/vps
cp .env.example .env        # JWT_SECRET و ADMIN_KEY را عوض کن
npm install
pm2 start src/server.js --name hamfilm
# + Nginx با SSL و هدرهای Upgrade (برای WebSocket):
#   proxy_set_header Upgrade $http_upgrade;
#   proxy_set_header Connection "upgrade";
```

یا با داکر: `docker compose up -d --build`

---

## ۸) متغیرهای محیطی

| متغیر | کلادفلر | VPS | توضیح |
|---|---|---|---|
| `JWT_SECRET` | `wrangler secret put` | `.env` | کلید امضای توکن (حداقل ۳۲ کاراکتر تصادفی) |
| `ADMIN_KEY` | `wrangler secret put` | `.env` | کلید ورود پنل ادمین |
| `PORT` | — | `.env` (پیش‌فرض 8080) | پورت سرور |
| `CORS_ORIGIN` | — | `.env` (پیش‌فرض `*`) | دامنه مجاز — برای production دامنه سایت را بگذار |

---

## ۹) داده‌های اولیه (Seed)

با اجرای `schema.sql` (کلادفلر) یا خودکار (VPS) اینها ساخته می‌شوند:

- **تنظیمات:** `registrationRequired=false`, `maintenance=false`, `subscriptionsEnabled=false`, `welcomeMessage=خوش آمدید!`
- **پلن‌ها:** پایه (۵۰ هزار تومان) / محبوب (۱۵۰ هزار - ۱۰٪ تخفیف) / ویژه (۳۰۰ هزار - ۲۰٪ تخفیف)

تغییر این‌ها از پنل ادمین: `POST /api/admin/settings`

---

## ۱۰) نکات مهم

1. **پروتکل WebSocket تغییر نکرده** — سایت فعلی تقریباً بدون تغییر به این بک‌اند وصل می‌شود؛ فقط آدرس‌ها عوض می‌شوند.
2. **پیام `file` جدید است** — برای پشتیبانی از «انتخاب فیلم از گوشی» در اپ اندروید. فرانت سایت می‌تواند از آن برای نمایش «در حال پخش: نام فایل» استفاده کند.
3. **`download_links`** در مدل فیلم جدید است — برای نمایش لینک‌های دانلود در صفحه فیلم.
4. **محدودیت پیام چت:** ۵۰۰ پیام آخر نگه داشته می‌شود.
5. **میزبان:** اولین نفری که وارد اتاق می‌شود میزبان است؛ اگر برود، نفر بعدی میزبان می‌شود.
6. **امنیت:** هرگز `JWT_SECRET` و `ADMIN_KEY` را در کد/git نگذارید.

---

*سوالی بود: همین سند + کد کامل بک‌اند در ریپو موجود است.*
