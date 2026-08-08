# بک‌اند هم‌فیلم — Cloudflare Workers

نسخه کلادفلری بک‌اند هم‌فیلم: **REST API + WebSocket** با D1 (دیتابیس) و Durable Objects (اتاق‌ها).

## ساختار

```
cloudflare/
├── wrangler.toml      ← تنظیمات Worker (D1 + Durable Objects)
├── schema.sql         ← اسکیمای دیتابیس (اول اجرا کن)
└── src/
    ├── index.js       ← روتر REST + پنل ادمین
    ├── room.js        ← Durable Object اتاق‌ها (WebSocket)
    └── auth.js        ← JWT + هش رمز
```

## راه‌اندازی

```bash
# 1) نصب Wrangler
npm i -g wrangler

# 2) ساخت دیتابیس D1 و گرفتن ID آن
wrangler d1 create hamfilm
# → database_id را در wrangler.toml بگذار

# 3) اجرای اسکیما
wrangler d1 execute hamfilm --remote --file=schema.sql

# 4) کلیدهای امن
wrangler secret put JWT_SECRET      # یک رشته تصادفی بلند
wrangler secret put ADMIN_KEY       # کلید پنل ادمین

# 5) دیپلوی
wrangler deploy
```

## تست سریع

```bash
# تنظیمات عمومی
curl https://YOUR-WORKER.workers.dev/api/settings/public

# ساخت اتاق
curl -X POST https://YOUR-WORKER.workers.dev/api/rooms \
  -H "Content-Type: application/json" \
  -d '{"name":"اتاق تست","videoUrl":"https://example.com/movie.mp4"}'

# لاگین ادمین
curl -X POST https://YOUR-WORKER.workers.dev/api/admin/login \
  -H "Content-Type: application/json" -d '{"key":"YOUR_ADMIN_KEY"}'
```

## نکته اتصال به سایت فعلی

اگر سایت فعلی (SPA) روی یک Worker دیگر است، می‌توانی:

- **گزینه A:** همین Worker را به‌عنوان `api.*` یا مسیر `/api/*` از Worker سایت، route کنی (`wrangler routes`)
- **گزینه B:** کد `src/index.js` و `src/room.js` را در همان Worker سایت ادغام کنی (روترها با هم تداخلی ندارند)
- سپس در اپ اندروید از صفحه «تنظیمات» آدرس را روی همین Worker بگذار

## امنیت (الزامی)

- `JWT_SECRET` و `ADMIN_KEY` هرگز در کد نباشند — فقط با `wrangler secret`
- Rate-limit روی `/api/admin/login` را در صورت نیاز با Rate Limiting API کلادفلر اضافه کن
- CORS (`*`) فعلاً برای توسعه باز است؛ برای production به دامنه خودت محدودش کن:
  `Access-Control-Allow-Origin: https://hamfilm.ir`
