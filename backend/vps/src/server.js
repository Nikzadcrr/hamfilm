// ---------- نقطه ورود سرور VPS هم‌فیلم ----------
import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import http from 'http';
import { attachWebSocket } from './ws.js';
import routes from './routes.js';
import { db } from './db.js';

const app = express();
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '1mb' }));

// سلامت
app.get('/health', (req, res) => res.json({ ok: true, time: Date.now() }));

// همه API ها
app.use('/api', routes);

// 404
app.use((req, res) => res.status(404).json({ error: 'مسیر یافت نشد' }));

// خطای سراسری
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: 'خطای سرور' });
});

const server = http.createServer(app);
attachWebSocket(server);

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log(`✅ هم‌فیلم VPS روی پورت ${PORT} اجرا شد`);
  console.log(`   REST: http://localhost:${PORT}/api`);
  console.log(`   WS:   ws://localhost:${PORT}/ws/{code}`);
});
