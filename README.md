# 🎬 پروژه هم‌فیلم — اپ اندروید + بک‌اند (کلادفلر و VPS)

این پروژه شامل **۳ بخش** است که با هم کار می‌کنند:

```
hamfilm/
├── android/                  📱 اپ اندروید هم‌فیلم (Kotlin + Jetpack Compose)
├── backend/
│   ├── cloudflare/           ☁️ بک‌اند برای Cloudflare Workers (D1 + Durable Objects)
│   └── vps/                  🖥️ بک‌اند برای VPS (Node.js + Express + SQLite)
└── README.md                 ← همین فایل
```

> ⚠️ **توجه:** بک‌اند VPS الان در محیط تست همین‌جا اجرا شده و تست‌شده است (API + WebSocket هر دو کار می‌کنند).
> اپ اندروید باید با **Android Studio** بیلد شود (توضیح پایین).

---

## ۱) 📱 اپ اندروید

### امکانات (الهام‌گرفته از اپ «ببینیم» + متصل به بک‌اند هم‌فیلم)

- 🏠 **خانه**: پیام خوش‌آمد/اعلان از سرور، فیلم‌های ویژه، ورود سریع
- 🎬 **ساخت اتاق**: نام، لینک ویدیو (MP4/HLS/یوتیوب + لینک‌های نمونه)، رمز اختیاری، آواتار
- 🔑 **ورود با کد اتاق** (مهمان هم می‌تواند — مثل سایت)
- 📺 **اتاق پخش هم‌زمان**: ExoPlayer + همگام‌سازی play/pause/seek بین همه + تعویض ویدیو توسط میزبان
- 💬 **چت زنده** (۵۰۰ پیام اخیر + اندیکاتور تایپینگ)
- ⚡ **واکنش‌های لحظه‌ای** روی پلیر (Emoji Blast: ❤️😂😮👍😢🔥🎬🍿)
- 👥 **مدیریت اعضا برای میزبان**: اخراج (kick)، سکوت (mute)، لیست اعضا با آواتار
- 🎞️ **آرشیو فیلم‌ها**: گرید + ژانر + جستجو + صفحه جزئیات
- 🔐 **ورود/ثبت‌نام** با JWT (ذخیره در SharedPreferences امن، بدون لاگ حساس)
- 💎 **پلن‌ها و پرداخت** (Zarinpal) + وضعیت اشتراک
- 🎟️ **تیکت پشتیبانی** (لیست/جدید/گفتگو)
- ⚙️ **تنظیمات سرور**: جابه‌جایی بین کلادفلر و VPS با یک لمس + تست اتصال
- 👋 **راهنمای قدم‌به‌قدم** اولین بار (الهام از ببینیم)
- 🎨 **تم تیره با گرادیان بنفش→فیروزه‌ای** (هویت سایت) + فونت وزیرمتن + RTL کامل

### بیلد کردن (روی سیستم خودت)

1. **Android Studio** (نسخه Ladybug یا جدیدتر) را نصب کن
2. `File → Open` → پوشه `hamfilm/android` را باز کن
3. صبر کن Gradle Sync تمام شود (اولین بار چند دقیقه طول می‌کشد)
4. `Run ▶` روی یک دستگاه/شبیه‌ساز

> هیچ کلیدی لازم نیست؛ اپ پیش‌فرض به `https://hamfilm-worker.ai-showcase-shir.workers.dev/` وصل می‌شود
> و از صفحه «تنظیمات سرور» (پروفایل ← تنظیمات) می‌توانی آدرس VPS را بدهی.

---

## ۲) ☁️ بک‌اند کلادفلر

راه‌اندازی: `backend/cloudflare/README.md` — خلاصه:

```bash
npm i -g wrangler
wrangler d1 create hamfilm            # → database_id را در wrangler.toml بگذار
wrangler d1 execute hamfilm --remote --file=schema.sql
wrangler secret put JWT_SECRET
wrangler secret put ADMIN_KEY
wrangler deploy
```

**نکته:** اگر سایت فعلی روی Worker دیگری است، این Worker را route کن یا کد را در همان Worker ادغام کن.

---

## ۳) 🖥️ بک‌اند VPS

راه‌اندازی: `backend/vps/README.md` — خلاصه (روی سرور Ubuntu):

```bash
cd backend/vps
cp .env.example .env          # JWT_SECRET و ADMIN_KEY را عوض کن
npm install
pm2 start src/server.js --name hamfilm
# + Nginx با SSL و هدرهای Upgrade (برای WebSocket)
```

یا با داکر:

```bash
docker compose up -d --build
```

---

## ۴) 🔌 پروتکل یکسان — تفاوت فقط در آدرس

اپ اندروید **هیچ فرقی** بین کلادفلر و VPS نمی‌بیند؛ فقط آدرس Base URL عوض می‌شود:

| سرور | Base URL (در تنظیمات اپ) |
|---|---|
| کلادفلر | `https://YOUR-WORKER.workers.dev/` |
| VPS | `https://api.hamfilm.ir/` |

هر دو همین اندپوینت‌ها را دارند:

```
POST /api/auth/register      GET /api/movies?page=&genre=&q=
POST /api/auth/login         GET /api/movies/featured
GET  /api/auth/me            GET /api/movies/genres
POST /api/auth/logout        GET /api/movies/{slug}
POST /api/rooms              GET /api/plans
GET  /api/rooms/{code}       GET /api/subscriptions/me
POST /api/reports            POST /api/subscriptions/checkout
GET/POST /api/support/tickets  GET/POST /api/ads, /api/faqs, /api/articles
GET /api/settings/public     /api/admin/* (کلید ادمین)
WS /ws/{roomCode}            ← پروتکل اتاق‌ها
```

پروتکل WebSocket اتاق‌ها (هر دو سرور):

| نوع پیام | کاربرد |
|---|---|
| `join` / `leave` | ورود/خروج (اولین نفر میزبان می‌شود) |
| `peers` | لیست زنده اعضا |
| `chat` / `reaction` / `typing` | چت، واکنش، تایپینگ |
| `control` | همگام‌سازی پخش: `play` / `pause` / `seek` / `video` (تغییر ویدیو فقط میزبان) |
| `system` | رویدادهای سیستمی |
| `kick` / `mute` / `lock` / `rename` | مدیریت میزبان |
| `presence` | آمادگی همگام‌سازی فایل محلی (فاز ۲) |

---

## ۵) 🔒 نکات امنیتی (درس‌گرفته از بررسی اپ «ببینیم»)

در این پروژه **عمداً** اشتباه‌های اپ ببینیم تکرار نشده:

- ✅ SSL همیشه فعال؛ لاگ فقط در debug و با سطح BASIC (بدون توکن)
- ✅ `allowBackup=false` + ذخیره توکن در SharedPreferences خصوصی
- ✅ `usesCleartextTraffic=false` — فقط HTTPS
- ✅ پنل ادمین فقط با کلید ادمین (از env) — همه اندپوینت‌ها 401 بدون کلید
- ⚠️ برای production: `CORS_ORIGIN` را به دامنه خودت محدود کن و روی `/api/admin/login` rate-limit بگذار

---

## ۶) فازهای بعدی (پیشنهادی)

1. **نوتیفیکیشن** (FCM) — پیام جدید / ورود دوست
2. **پخش پس‌زمینه + PiP** — تماشا در پنجره شناور
3. **همگام‌سازی فایل محلی** — پروتکل `presence` آماده است
4. **چت صوتی WebRTC** — نیاز به TURN + سیگنالینگ (الگوی ببینیم موجود است)
5. **پنل ادمین موبایل** — API های ادمین آماده‌اند
