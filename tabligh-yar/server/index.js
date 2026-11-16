import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import multer from 'multer';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import { isEitaaChannelUrl, detectImageExtension, calculatePrice, verifyEitaaInitData } from './validation.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: true } : undefined });
const PORT = Number(process.env.PORT || 3000);
const isProd = process.env.NODE_ENV === 'production';
const secret = process.env.SESSION_SECRET || '';
if (isProd && (secret.length < 32 || !process.env.EITAA_BOT_TOKEN || !process.env.APP_ORIGIN?.startsWith('https://'))) {
  throw new Error('Production requires SESSION_SECRET (32+ chars), EITAA_BOT_TOKEN and HTTPS APP_ORIGIN.');
}
if (!isProd && secret.length < 16) console.warn('Set a strong SESSION_SECRET before deployment.');

const storageDir = path.resolve(__dirname, '../storage/receipts');
fs.mkdirSync(storageDir, { recursive: true, mode: 0o700 });
fs.chmodSync(storageDir, 0o700);
app.disable('x-powered-by');
app.use(helmet({ contentSecurityPolicy: { directives: {
  defaultSrc: ["'self'"], scriptSrc: ["'self'", 'https://developer.eitaa.com'],
  styleSrc: ["'self'", "'unsafe-inline'"], imgSrc: ["'self'", 'data:', 'blob:'],
  connectSrc: ["'self'"], objectSrc: ["'none'"], baseUri: ["'self'"],
  frameAncestors: ["'self'", 'https://eitaa.com', 'https://*.eitaa.com']
}}}));
app.use(express.json({ limit: '300kb' }));
app.use(cookieParser());
app.use(express.static(path.resolve(__dirname, '../public')));

const authLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 12, standardHeaders: true, legacyHeaders: false });
const orderLimiter = rateLimit({ windowMs: 60 * 60 * 1000, limit: 15, standardHeaders: true, legacyHeaders: false });
const adminLimiter = rateLimit({ windowMs: 60 * 1000, limit: 60, standardHeaders: true, legacyHeaders: false });

function setCsrf(res) {
  const token = crypto.randomBytes(24).toString('hex');
  res.cookie('ty_csrf', token, { httpOnly: false, secure: isProd, sameSite: 'strict', path: '/' });
  return token;
}
function csrf(req, res, next) {
  const cookie = req.cookies.ty_csrf;
  const header = req.get('x-csrf-token');
  if (!cookie || !header || cookie.length !== header.length || !crypto.timingSafeEqual(Buffer.from(cookie), Buffer.from(header))) {
    return res.status(403).json({ error: 'درخواست نامعتبر است؛ صفحه را تازه‌سازی کنید.' });
  }
  next();
}
function signSession(payload) {
  return jwt.sign(payload, secret, { expiresIn: '12h', issuer: 'tabligh-yar' });
}
function auth(req, res, next) {
  try {
    req.session = jwt.verify(req.cookies.ty_session || '', secret, { issuer: 'tabligh-yar' });
    next();
  } catch { res.status(401).json({ error: 'ابتدا وارد شوید.' }); }
}
function adminOnly(req, res, next) {
  if (req.session?.role !== 'admin') return res.status(403).json({ error: 'دسترسی مدیر لازم است.' });
  next();
}
function safeHttpsUrl(value) {
  try { const url = new URL(value); return url.protocol === 'https:'; } catch { return false; }
}

app.get('/api/health', async (_req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, database: 'connected' }); }
  catch { res.status(503).json({ ok: false, database: 'unavailable' }); }
});
app.get('/api/csrf', (_req, res) => res.json({ ok: true, csrfToken: setCsrf(res) }));
app.get('/api/config', async (_req, res, next) => {
  try {
    const { rows } = await pool.query('SELECT price_per_10000,min_views,max_views FROM app_settings WHERE id=1');
    if (!rows[0]) return res.status(503).json({ error: 'تنظیمات اولیه پایگاه داده موجود نیست؛ schema.sql را اجرا کنید.' });
    res.json(rows[0]);
  } catch (err) { next(err); }
});
app.post('/api/auth/eitaa', authLimiter, csrf, (req, res) => {
  const user = verifyEitaaInitData(req.body?.initData, process.env.EITAA_BOT_TOKEN);
  if (!user) {
    if (!isProd && process.env.DEV_ALLOW_TEST_USER === 'true') {
      const test = { id: 'local-test-user', name: 'کاربر آزمایشی' };
      res.cookie('ty_session', signSession({ role: 'customer', ...test }), { httpOnly: true, secure: isProd, sameSite: 'strict', maxAge: 12 * 3600 * 1000, path: '/' });
      return res.json({ ok: true, user: test, dev: true });
    }
    return res.status(401).json({ error: 'اطلاعات ورود ایتا معتبر نیست.' });
  }
  res.cookie('ty_session', signSession({ role: 'customer', ...user }), { httpOnly: true, secure: isProd, sameSite: 'strict', maxAge: 12 * 3600 * 1000, path: '/' });
  res.json({ ok: true, user });
});
app.post('/api/auth/admin', authLimiter, csrf, async (req, res, next) => {
  try {
    const { username, password } = req.body || {};
    if (typeof username !== 'string' || typeof password !== 'string' || username.length > 80 || password.length > 200) return res.status(400).json({ error: 'اطلاعات ورود نامعتبر است.' });
    const { rows } = await pool.query('SELECT username,password_hash FROM admins WHERE username=$1', [username]);
    const valid = rows[0] && await bcrypt.compare(password, rows[0].password_hash);
    if (!valid) return res.status(401).json({ error: 'نام کاربری یا رمز عبور اشتباه است.' });
    res.cookie('ty_session', signSession({ role: 'admin', username }), { httpOnly: true, secure: isProd, sameSite: 'strict', maxAge: 12 * 3600 * 1000, path: '/' });
    res.json({ ok: true });
  } catch (err) { next(err); }
});
app.post('/api/auth/logout', csrf, (_req, res) => {
  res.clearCookie('ty_session', { httpOnly: true, secure: isProd, sameSite: 'strict', path: '/' });
  res.json({ ok: true });
});

const upload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, storageDir),
    filename: (_req, file, cb) => cb(null, crypto.randomUUID() + file.safeExt)
  }),
  limits: { fileSize: 5 * 1024 * 1024, files: 1 },
  fileFilter: (_req, file, cb) => {
    const extByMime = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };
    if (!extByMime[file.mimetype]) return cb(new Error('فقط تصویر JPG، PNG یا WEBP مجاز است.'));
    file.safeExt = extByMime[file.mimetype];
    cb(null, true);
  }
});
app.post('/api/orders', auth, orderLimiter, csrf, upload.single('receipt'), async (req, res, next) => {
  let client;
  try {
    const views = Number(req.body.views);
    const channelUrl = String(req.body.channelUrl || '').trim();
    const adText = String(req.body.adText || '').trim();
    if (!req.file) throw Object.assign(new Error('تصویر رسید الزامی است.'), { status: 400 });
    const actualExt = detectImageExtension(fs.readFileSync(req.file.path));
    if (!actualExt || actualExt !== req.file.safeExt) throw Object.assign(new Error('محتوای فایل با فرمت تصویر انتخاب‌شده مطابقت ندارد.'), { status: 400 });
    if (!Number.isInteger(views) || views < 1000 || views > 200000 || views % 1000) throw Object.assign(new Error('تعداد بازدید باید بین ۱۰۰۰ تا ۲۰۰۰۰۰ و مضرب ۱۰۰۰ باشد.'), { status: 400 });
    if (!isEitaaChannelUrl(channelUrl)) throw Object.assign(new Error('لینک کانال باید از دامنهٔ رسمی ایتا باشد.'), { status: 400 });
    if (adText.length < 5 || adText.length > 5000) throw Object.assign(new Error('متن تبلیغ باید بین ۵ تا ۵۰۰۰ نویسه باشد.'), { status: 400 });
    const { rows: settings } = await pool.query('SELECT price_per_10000,min_views,max_views FROM app_settings WHERE id=1');
    if (!settings[0]) throw Object.assign(new Error('تنظیمات اولیه پایگاه داده موجود نیست.'), { status: 503 });
    if (views < settings[0].min_views || views > settings[0].max_views) throw Object.assign(new Error('تعداد بازدید خارج از محدوده تنظیم‌شده است.'), { status: 400 });
    const rate = BigInt(settings[0].price_per_10000);
    const price = calculatePrice(rate, views);
    client = await pool.connect();
    await client.query('BEGIN');
    const { rows } = await client.query(
      'INSERT INTO ad_orders(user_id,user_name,channel_url,ad_text,requested_views,quoted_price,price_per_10000_snapshot,receipt_path) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id,status,requested_views,quoted_price,created_at',
      [req.session.id, req.session.name || null, new URL(channelUrl).toString(), adText, views, price.toString(), rate.toString(), req.file.filename]
    );
    await client.query('INSERT INTO order_events(order_id,admin_username,old_status,new_status,note) VALUES($1,$2,NULL,$3,$4)', [rows[0].id, 'system', 'pending', 'سفارش توسط کاربر ثبت شد']);
    await client.query('COMMIT');
    res.status(201).json({ order: rows[0] });
  } catch (err) {
    if (client) await client.query('ROLLBACK').catch(() => {});
    if (req.file) await fs.promises.unlink(req.file.path).catch(() => {});
    next(err);
  } finally { client?.release(); }
});
app.get('/api/orders', auth, async (req, res, next) => {
  try {
    const { rows } = await pool.query('SELECT id,requested_views,quoted_price,status,admin_note,published_post_url,final_views,created_at,updated_at FROM ad_orders WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100', [req.session.id]);
    res.json({ orders: rows });
  } catch (err) { next(err); }
});
app.get('/api/admin/orders', auth, adminOnly, adminLimiter, async (_req, res, next) => {
  try {
    const { rows } = await pool.query('SELECT id,user_id,user_name,channel_url,ad_text,requested_views,quoted_price,status,admin_note,published_post_url,final_views,created_at FROM ad_orders ORDER BY created_at DESC LIMIT 300');
    res.json({ orders: rows });
  } catch (err) { next(err); }
});
app.get('/api/admin/orders/:id/receipt', auth, adminOnly, adminLimiter, async (req, res, next) => {
  try {
    const { rows } = await pool.query('SELECT receipt_path FROM ad_orders WHERE id=$1', [req.params.id]);
    if (!rows[0]) return res.sendStatus(404);
    const file = path.join(storageDir, path.basename(rows[0].receipt_path));
    if (!fs.existsSync(file)) return res.sendStatus(404);
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Cache-Control', 'private, no-store');
    res.type(path.extname(file)).sendFile(file);
  } catch (err) { next(err); }
});
app.patch('/api/admin/orders/:id', auth, adminOnly, adminLimiter, csrf, async (req, res, next) => {
  const allowed = ['pending','paid','payment_rejected','published','completed','cancelled'];
  const { status, note, publishedPostUrl, finalViews } = req.body || {};
  if (!allowed.includes(status)) return res.status(400).json({ error: 'وضعیت سفارش نامعتبر است.' });
  if (publishedPostUrl != null && publishedPostUrl !== '' && !safeHttpsUrl(publishedPostUrl)) return res.status(400).json({ error: 'لینک انتشار باید HTTPS باشد.' });
  if (finalViews != null && (!Number.isInteger(finalViews) || finalViews < 0)) return res.status(400).json({ error: 'تعداد بازدید نهایی نامعتبر است.' });
  let client;
  try {
    client = await pool.connect();
    await client.query('BEGIN');
    const old = await client.query('SELECT status FROM ad_orders WHERE id=$1 FOR UPDATE', [req.params.id]);
    if (!old.rows[0]) { await client.query('ROLLBACK'); return res.sendStatus(404); }
    const updated = await client.query(
      'UPDATE ad_orders SET status=$1,admin_note=$2,published_post_url=$3,final_views=$4,updated_at=NOW() WHERE id=$5 RETURNING id,status,admin_note,published_post_url,final_views',
      [status, typeof note === 'string' ? note.slice(0,1000) : null, publishedPostUrl || null, finalViews ?? null, req.params.id]
    );
    await client.query('INSERT INTO order_events(order_id,admin_username,old_status,new_status,note) VALUES($1,$2,$3,$4,$5)', [req.params.id, req.session.username, old.rows[0].status, status, typeof note === 'string' ? note.slice(0,1000) : null]);
    await client.query('COMMIT');
    res.json({ order: updated.rows[0] });
  } catch (err) { if (client) await client.query('ROLLBACK').catch(() => {}); next(err); }
  finally { client?.release(); }
});
app.patch('/api/admin/settings', auth, adminOnly, adminLimiter, csrf, async (req, res, next) => {
  try {
    const rate = Number(req.body?.pricePer10000);
    if (!Number.isSafeInteger(rate) || rate < 1 || rate > 1000000000) return res.status(400).json({ error: 'تعرفه نامعتبر است.' });
    const { rows } = await pool.query('UPDATE app_settings SET price_per_10000=$1,updated_at=NOW() WHERE id=1 RETURNING price_per_10000,min_views,max_views', [rate]);
    res.json({ settings: rows[0] });
  } catch (err) { next(err); }
});

app.use((err, _req, res, _next) => {
  console.error(err);
  if (err instanceof multer.MulterError) return res.status(400).json({ error: err.code === 'LIMIT_FILE_SIZE' ? 'حجم رسید حداکثر ۵ مگابایت است.' : 'فایل رسید بیش از حد مجاز است یا معتبر نیست.' });
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'ساختار درخواست JSON نامعتبر است.' });
  if (err.status === 400) return res.status(400).json({ error: err.message });
  if (String(err.message || '').startsWith('فقط تصویر')) return res.status(400).json({ error: err.message });
  res.status(500).json({ error: 'خطای داخلی سرور.' });
});

async function bootstrap() {
  await pool.query('SELECT 1');
  const username = process.env.ADMIN_USERNAME;
  const password = process.env.ADMIN_PASSWORD;
  if (username && password) {
    const hash = await bcrypt.hash(password, 12);
    await pool.query('INSERT INTO admins(username,password_hash) VALUES($1,$2) ON CONFLICT(username) DO NOTHING', [username, hash]);
  }
  app.listen(PORT, () => console.log('Tabligh Yar listening on port ' + PORT));
}
bootstrap().catch(err => { console.error('Startup failed:', err); process.exit(1); });
