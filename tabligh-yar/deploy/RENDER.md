# راهنمای استفاده از Render به‌عنوان میزبان بک‌اند

در این معماری Render فقط برنامه Node.js را اجرا می‌کند. دیتابیس و رسیدها باید خارج از فایل‌سیستم Render و در Supabase نگهداری شوند.

## ۱) محدودیت پلن رایگان

پلن رایگان Render ممکن است سرویس را در زمان بیکاری متوقف کند و فایل‌سیستم آن پایدار نیست. به همین دلیل:
- از PostgreSQL رایگان Render برای اطلاعات اصلی استفاده نکنید، مگر اینکه شرایط انقضا و نگهداری داده‌ها را پذیرفته باشید.
- Persistent Disk نسازید؛ این قابلیت ممکن است هزینه داشته باشد.
- رسیدها باید از طریق Supabase Storage خصوصی ذخیره شوند.
- خوابیدن سرویس می‌تواند باعث تأخیر در اولین بازدید شود.

## ۲) تنظیم Web Service

1. مخزن GitHub را متصل کنید.
2. شاخه `tabligh-yar` را انتخاب کنید.
3. Root Directory: `tabligh-yar`
4. Build Command: `npm install`
5. Start Command: `npm start`
6. Health Check Path: `/api/health`

## ۳) تنظیم Supabase

مراحل کامل ساخت PostgreSQL و bucket خصوصی در [FREE-HOSTING.md](FREE-HOSTING.md) آمده است. فایل `db/schema.sql` را در SQL Editor اجرا کنید و یک bucket خصوصی به نام `receipts` بسازید.

برای اتصال PostgreSQL، از رشته اتصال مناسب Supabase استفاده کنید. اگر میزبان فقط IPv4 دارد، از Connection Pooler سازگار Supabase استفاده کنید و مقادیر میزبان/پورت/نام کاربری را دقیقاً از داشبورد Supabase کپی کنید.

## ۴) متغیرهای محیطی Render

- `NODE_ENV=production`
- `DATABASE_URL` = رشته اتصال PostgreSQL از Supabase
- `SESSION_SECRET` = مقدار تصادفی حداقل ۳۲ نویسه
- `EITAA_BOT_TOKEN` = توکن رسمی ایتا
- `ADMIN_USERNAME` و `ADMIN_PASSWORD` = اطلاعات مدیر
- `SUPABASE_URL` = Project URL
- `SUPABASE_SERVICE_ROLE_KEY` = کلید محرمانه سمت سرور
- `SUPABASE_STORAGE_BUCKET=receipts`
- `DEV_ALLOW_TEST_USER=false`
- `APP_ORIGIN` = آدرس HTTPS نهایی

کلید `service_role` را هرگز در کد مرورگر، GitHub یا گفتگو قرار ندهید.

## ۵) آزمون نهایی

1. `/api/health` را بررسی کنید.
2. ورود واقعی ایتا را آزمایش کنید.
3. یک سفارش آزمایشی ثبت کنید.
4. دریافت رسید از پنل مدیر و عدم دسترسی کاربر عادی را بررسی کنید.
5. پس از redeploy، ماندگاری رسید را دوباره آزمایش کنید.

## هزینه

این راهنما برای استفاده از سهمیه رایگان نوشته شده است، اما شرایط پلن‌ها تغییر می‌کند. هیچ پلن پولی، دیسک پایدار یا روش پرداختی را بدون بررسی و اجازه صاحب حساب فعال نکنید.
