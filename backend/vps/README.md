# بک‌اند هم‌فیلم — VPS (Node.js)

نسخه VPS بک‌اند هم‌فیلم: **Express + WebSocket (ws) + SQLite** — دقیقاً هم‌پروتکل با نسخه Cloudflare،
پس اپ اندروید با هر دو به یک شکل کار می‌کند (فقط آدرس سرور در تنظیمات اپ عوض می‌شود).

## ساختار

```
vps/
├── package.json
├── Dockerfile
├── docker-compose.yml
├── .env.example        ← کپی به .env و مقداردهی
└── src/
    ├── server.js       ← نقطه ورود (Express + WS)
    ├── routes.js       ← همه REST endpoint ها
    ├── ws.js           ← اتاق‌های WebSocket
    ├── db.js           ← SQLite + seed
    └── auth.js         ← JWT + bcrypt
```

## اجرای محلی

```bash
npm install
cp .env.example .env        # JWT_SECRET و ADMIN_KEY را عوض کن
npm start                   # → http://localhost:8080
```

## اجرا با داکر (روی VPS)

```bash
cp .env.example .env
# مقادیر واقعی را در .env بگذار (JWT_SECRET طولانی تصادفی + ADMIN_KEY)
docker compose up -d --build
```

دیتابیس در `./data/hamfilm.db` ساخته می‌شود (حجم آن را backup بگیر!).

## نصب روی VPS واقعی (بدون داکر)

```bash
# نصب Node 20 روی Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs git

git clone <repo> hamfilm && cd hamfilm/backend/vps
npm install
cp .env.example .env && nano .env

# اجرای دائمی با PM2
sudo npm i -g pm2
pm2 start src/server.js --name hamfilm
pm2 save && pm2 startup
```

## ریورس پروکسی + SSL (اجباری برای production)

با Nginx + Let's Encrypt، دامنه را به پورت 8080 وصل کن:

```nginx
server {
    listen 443 ssl;
    server_name api.hamfilm.ir;

    ssl_certificate     /etc/letsencrypt/live/api.hamfilm.ir/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.hamfilm.ir/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;      # برای WebSocket
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 300s;
    }
}
```

> ⚠️ WebSocket بدون این دو خط `Upgrade` کار نمی‌کند.

## تفاوت با نسخه کلادفلر

| | Cloudflare Workers | VPS (Node.js) |
|---|---|---|
| دیتابیس | D1 | SQLite (فایل) |
| اتاق‌ها | Durable Objects | حافظه سرور (Map) |
| قیمت | رایگان تا سقف استفاده | هزینه سرور |
| مقیاس‌پذیری | خودکار | دستی (چند instance = نیاز به Redis) |
| وابستگی | ندارد | Node.js + دیتابیس |

**پیشنهاد:** شروع با Cloudflare (رایگان و ساده) — اگر ترافیک بالا رفت یا امکانات بیشتری خواستی، VPS.
