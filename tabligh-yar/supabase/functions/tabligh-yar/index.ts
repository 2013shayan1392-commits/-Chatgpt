const HTML = "<!doctype html>\n<html lang=\"fa\" dir=\"rtl\">\n<head>\n  <meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n  <meta name=\"theme-color\" content=\"#101827\"><title>تبلیغ‌یار</title>\n  <link rel=\"stylesheet\" href=\"/functions/v1/tabligh-yar/styles.css\">\n  <script src=\"https://developer.eitaa.com/eitaa-web-app.js\"></script>\n</head>\n<body>\n  <main class=\"shell\">\n    <header><div class=\"mark\">ت</div><div><h1>تبلیغ‌یار</h1><p>ثبت و پیگیری سفارش تبلیغات کانال</p></div></header>\n    <section class=\"panel\">\n      <h2>محاسبه هزینه</h2>\n      <label for=\"views\">تعداد بازدید درخواستی</label>\n      <input id=\"views\" type=\"number\" min=\"1000\" max=\"200000\" step=\"1000\" value=\"10000\">\n      <div class=\"price\"><span>هزینه تقریبی</span><strong id=\"price\">۹۰٬۰۰۰ تومان</strong></div>\n      <p class=\"hint\">تعرفه فعلی: ۹۰٬۰۰۰ تومان به ازای هر ۱۰٬۰۰۰ بازدید</p>\n    </section>\n    <section class=\"panel\"><h2>اطلاعات پرداخت</h2><p id=\"paymentInstructions\" class=\"payment-instructions\">اطلاعات پرداخت هنوز توسط مدیر ثبت نشده است. تا زمان ثبت اطلاعات، رسید ارسال نکنید.</p></section>\n    <section class=\"panel\">\n      <h2>ثبت سفارش</h2>\n      <form id=\"orderForm\">\n        <label for=\"channelUrl\">لینک کانال ایتا</label><input id=\"channelUrl\" name=\"channelUrl\" type=\"url\" placeholder=\"https://eitaa.com/...\" required>\n        <label for=\"adText\">متن تبلیغ</label><textarea id=\"adText\" name=\"adText\" rows=\"5\" minlength=\"5\" maxlength=\"5000\" required></textarea>\n        <label for=\"receipt\">تصویر رسید پرداخت (JPG، PNG یا WEBP؛ حداکثر ۵ مگابایت)</label><input id=\"receipt\" name=\"receipt\" type=\"file\" accept=\"image/jpeg,image/png,image/webp\" required>\n        <button type=\"submit\">ثبت سفارش</button>\n      </form>\n    </section>\n    <section class=\"panel\"><div class=\"row\"><h2>سفارش‌های من</h2><button id=\"refresh\" class=\"secondary\" type=\"button\">به‌روزرسانی</button></div><div id=\"orders\" class=\"orders\">برای مشاهده سفارش‌ها وارد ایتا شوید.</div></section>\n    <section class=\"panel\">\n      <button id=\"adminToggle\" class=\"secondary\" type=\"button\">ورود مدیر</button>\n      <form id=\"adminForm\" class=\"hidden\">\n        <label for=\"adminUser\">نام کاربری مدیر</label><input id=\"adminUser\" autocomplete=\"username\" required>\n        <label for=\"adminPass\">رمز عبور</label><input id=\"adminPass\" type=\"password\" autocomplete=\"current-password\" required>\n        <button type=\"submit\">ورود به پنل مدیریت</button>\n      </form>\n      <div id=\"adminPanel\" class=\"hidden\"><div class=\"row\"><h2>مدیریت سفارش‌ها</h2><div><button id=\"adminRefresh\" class=\"secondary\" type=\"button\">به‌روزرسانی</button><button id=\"adminLogout\" class=\"secondary\" type=\"button\">خروج مدیر</button></div></div>\n        <label for=\"newRate\">تعرفه هر ۱۰٬۰۰۰ بازدید (تومان)</label><input id=\"newRate\" type=\"number\" min=\"1\"><label for=\"paymentInstructionsInput\">اطلاعات پرداخت برای مشتریان</label><textarea id=\"paymentInstructionsInput\" rows=\"4\" maxlength=\"2000\" placeholder=\"اطلاعات پرداخت را اینجا وارد کنید\"></textarea><button id=\"saveRate\" type=\"button\">ذخیره تنظیمات</button>\n        <div id=\"adminOrders\" class=\"orders\"></div>\n      </div>\n    </section>\n    <p id=\"message\" role=\"status\" aria-live=\"polite\"></p>\n    <footer>پرداخت و انتشار تبلیغ پس از بررسی دستی مدیر انجام می‌شود.</footer>\n  </main>\n  <script src=\"/functions/v1/tabligh-yar/app.js\"></script>\n</body></html>";
const APP_JS = "const $ = id => document.getElementById(id);\nconst API_BASE = window.TABLIGH_API_BASE || (location.pathname.startsWith('/functions/v1/tabligh-yar') ? '/functions/v1/tabligh-yar' : '');\nlet csrfToken = '', settings = { price_per_10000: 90000, min_views: 1000, max_views: 200000, payment_instructions: '' }, eitaaUser = false;\nconst money = n => Number(n).toLocaleString('fa-IR') + ' تومان';\nconst numberFa = n => Number(n).toLocaleString('fa-IR');\nconst escapeHtml = s => String(s ?? '').replace(/[&<>\"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[c]));\nasync function api(url, options = {}) {\n  const headers = { ...(options.headers || {}) };\n  if (options.method && options.method !== 'GET') headers['x-csrf-token'] = csrfToken;\n  const targetUrl = url.startsWith('/api/') ? API_BASE + url : url;\n  const response = await fetch(targetUrl, { ...options, headers, credentials: 'same-origin' });\n  const data = await response.json().catch(() => ({}));\n  if (!response.ok) throw new Error(data.error || 'خطا در ارتباط با سرور');\n  return data;\n}\nasync function refreshCsrf() { const d = await api('/api/csrf'); csrfToken = d.csrfToken; }\nasync function loadSettings() {\n  settings = await api('/api/config');\n  $('newRate').value = settings.price_per_10000;\n  $('paymentInstructionsInput').value = settings.payment_instructions || '';\n  updatePaymentInstructions();\n  updatePrice();\n}\nfunction updatePaymentInstructions() {\n  const value = String(settings.payment_instructions || '').trim();\n  $('paymentInstructions').textContent = value || 'اطلاعات پرداخت هنوز توسط مدیر ثبت نشده است. تا زمان ثبت اطلاعات، رسید ارسال نکنید.';\n}\nfunction updatePrice() {\n  const views = Number($('views').value) || 0;\n  $('price').textContent = money(Math.round(Number(settings.price_per_10000) * views / 10000));\n  document.querySelector('.hint').textContent = 'تعرفه فعلی: ' + money(settings.price_per_10000) + ' به ازای هر ۱۰٬۰۰۰ بازدید';\n}\nasync function signInEitaa() {\n  const webApp = window.Eitaa?.WebApp;\n  if (!webApp?.initData) return false;\n  webApp.ready?.(); webApp.expand?.();\n  await api('/api/auth/eitaa', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({initData:webApp.initData}) });\n  eitaaUser = true;\n  return true;\n}\nfunction statusName(s) {\n  return ({pending:'در انتظار بررسی',paid:'پرداخت تأیید شد',payment_rejected:'رسید رد شد',published:'منتشر شد',completed:'تکمیل شد',cancelled:'لغو شد'})[s] || s;\n}\nasync function loadOrders() {\n  try {\n    const data = await api('/api/orders');\n    $('orders').innerHTML = data.orders.length ? data.orders.map(o => `<article class=\"order\"><b>سفارش #${numberFa(o.id)}</b><br>${numberFa(o.requested_views)} بازدید · ${money(o.quoted_price)}<br>وضعیت: ${statusName(o.status)}${o.final_views != null ? '<br>بازدید نهایی: '+numberFa(o.final_views):''}${o.admin_note ? '<br>یادداشت مدیر: '+escapeHtml(o.admin_note):''}${o.published_post_url ? '<br><a target=\"_blank\" rel=\"noopener noreferrer\" href=\"'+escapeHtml(o.published_post_url)+'\">لینک انتشار</a>':''}<small>${new Date(o.created_at).toLocaleString('fa-IR')}</small></article>`).join('') : 'هنوز سفارشی ثبت نکرده‌اید.';\n  } catch { $('orders').textContent = 'برای مشاهده سفارش‌ها، برنامه را از داخل ایتا باز کنید.'; }\n}\n$('views').addEventListener('input', updatePrice);\n$('orderForm').addEventListener('submit', async e => {\n  e.preventDefault(); $('message').textContent = 'در حال ثبت سفارش…';\n  try {\n    if (!String(settings.payment_instructions || '').trim()) throw new Error('اطلاعات پرداخت هنوز توسط مدیر ثبت نشده است؛ فعلاً امکان ثبت سفارش وجود ندارد.');\n    if (!eitaaUser && !(await signInEitaa())) throw new Error('برای ثبت سفارش، مینی‌اپ را از داخل ایتا باز کنید.');\n    const file = $('receipt').files[0];\n    if (!file) throw new Error('تصویر رسید را انتخاب کنید.');\n    const form = new FormData();\n    form.append('views', $('views').value); form.append('channelUrl', $('channelUrl').value);\n    form.append('adText', $('adText').value); form.append('receipt', file);\n    const result = await api('/api/orders', {method:'POST',body:form});\n    $('message').textContent = 'سفارش شماره ' + numberFa(result.order.id) + ' ثبت شد؛ پس از بررسی رسید، وضعیت آن به‌روزرسانی می‌شود.';\n    $('orderForm').reset(); $('views').value = 10000; updatePrice(); await loadOrders();\n  } catch(err) { $('message').textContent = err.message; }\n});\n$('refresh').onclick = loadOrders;\n$('adminToggle').onclick = () => $('adminForm').classList.toggle('hidden');\n$('adminForm').addEventListener('submit', async e => {\n  e.preventDefault();\n  try {\n    await api('/api/auth/admin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:$('adminUser').value,password:$('adminPass').value})});\n    $('adminForm').classList.add('hidden'); $('adminPanel').classList.remove('hidden'); $('adminToggle').classList.add('hidden'); await loadAdminOrders();\n  } catch(err) { $('message').textContent = err.message; }\n});\nasync function loadAdminOrders() {\n  const data = await api('/api/admin/orders');\n  $('adminOrders').innerHTML = data.orders.length ? data.orders.map(o => `<article class=\"order\"><b>#${numberFa(o.id)} · ${escapeHtml(o.user_name || o.user_id)}</b><br>${numberFa(o.requested_views)} بازدید · ${money(o.quoted_price)}<br><a href=\"${escapeHtml(o.channel_url)}\" target=\"_blank\" rel=\"noopener noreferrer\">کانال</a> · <a href=\"${API_BASE}/api/admin/orders/${encodeURIComponent(o.id)}/receipt\" target=\"_blank\" rel=\"noopener noreferrer\">مشاهده رسید</a><p>${escapeHtml(o.ad_text)}</p><small>وضعیت: ${statusName(o.status)}${o.final_views != null ? ' · بازدید نهایی: '+numberFa(o.final_views):''}</small>${o.admin_note ? '<p>یادداشت: '+escapeHtml(o.admin_note)+'</p>':''}<div class=\"actions\">${['paid','payment_rejected','published','completed','cancelled'].map(s=>`<button type=\"button\" data-id=\"${o.id}\" data-status=\"${s}\" class=\"secondary\">${statusName(s)}</button>`).join('')}</div></article>`).join('') : 'هنوز سفارشی ثبت نشده است.';\n  $('adminOrders').querySelectorAll('button[data-id]').forEach(btn => btn.onclick = async () => {\n    const note = prompt('یادداشت مدیر (اختیاری):') ?? '';\n    let publishedPostUrl = null, finalViews = null;\n    if (btn.dataset.status === 'published') publishedPostUrl = prompt('لینک پست منتشرشده در ایتا (اختیاری):') || null;\n    if (btn.dataset.status === 'completed') {\n      const raw = prompt('تعداد بازدید نهایی (فقط عدد؛ اختیاری):');\n      if (raw !== null && raw.trim() !== '') {\n        finalViews = Number(raw);\n        if (!Number.isSafeInteger(finalViews) || finalViews < 0) { $('message').textContent = 'تعداد بازدید نهایی نامعتبر است.'; return; }\n      }\n    }\n    try {\n      await api('/api/admin/orders/'+encodeURIComponent(btn.dataset.id),{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:btn.dataset.status,note,publishedPostUrl,finalViews})});\n      await loadAdminOrders(); $('message').textContent = 'سفارش به‌روزرسانی شد.';\n    } catch(err) { $('message').textContent = err.message; }\n  });\n}\n$('adminRefresh').onclick = () => loadAdminOrders().catch(e => $('message').textContent=e.message);\n$('adminLogout').onclick = async () => {\n  try {\n    await api('/api/auth/logout',{method:'POST'});\n    $('adminPanel').classList.add('hidden'); $('adminToggle').classList.remove('hidden'); $('adminPass').value = '';\n    $('message').textContent = 'از پنل مدیریت خارج شدید.';\n  } catch(err) { $('message').textContent = err.message; }\n};\n$('saveRate').onclick = async () => {\n  try {\n    const r = await api('/api/admin/settings',{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({pricePer10000:Number($('newRate').value),paymentInstructions:$('paymentInstructionsInput').value})});\n    settings=r.settings; $('paymentInstructionsInput').value=settings.payment_instructions || ''; updatePaymentInstructions(); updatePrice(); $('message').textContent='تنظیمات ذخیره شد.';\n  } catch(err) { $('message').textContent=err.message; }\n};\n(async () => {\n  try { await refreshCsrf(); await loadSettings(); await signInEitaa(); await loadOrders(); }\n  catch(err) { $('message').textContent=err.message; }\n})();\n";
const CSS = ":root{color-scheme:dark;font-family:Tahoma,Arial,sans-serif;background:#0b1220;color:#e8eef8}*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 80% 0,#1c3150 0,transparent 38%),#0b1220;min-height:100vh}.shell{max-width:720px;margin:auto;padding:24px 16px}header{display:flex;align-items:center;gap:14px;margin:10px 0 26px}.mark{width:48px;height:48px;border-radius:15px;background:#42c5a7;color:#06251f;display:grid;place-items:center;font-size:26px;font-weight:bold}h1{font-size:23px;margin:0}header p,.hint,footer{color:#91a6c3;font-size:12px;margin:5px 0}.panel{background:#121e30;border:1px solid #293e59;border-radius:18px;padding:20px;margin:16px 0;box-shadow:0 14px 36px #0003}.panel h2{font-size:17px;margin:0 0 17px}label{display:block;font-size:13px;color:#c2d0e2;margin:14px 0 8px}input,textarea{display:block;width:100%;background:#0b1525;border:1px solid #354b68;border-radius:10px;padding:12px;color:#f4f7fb;font:inherit;outline:none}input:focus,textarea:focus{border-color:#55cfb2}textarea{resize:vertical;line-height:1.8}.price{display:flex;justify-content:space-between;align-items:center;background:#0b2d32;border:1px solid #1c5d59;padding:14px;border-radius:12px;margin:16px 0;color:#b4d8d1}.price strong{color:#73e0c5;font-size:17px}button{border:0;border-radius:10px;padding:11px 15px;background:#43c5a7;color:#06251f;font:inherit;font-weight:bold;cursor:pointer;margin-top:12px}button.secondary{background:#253b56;color:#e3edf9;font-weight:normal}.row{display:flex;align-items:center;justify-content:space-between;gap:12px}.row h2{margin:0}.row input{flex:1}.orders{display:grid;gap:12px;margin-top:14px;color:#a9bad0;line-height:1.9}.order{border:1px solid #344963;background:#0d192a;border-radius:12px;padding:13px}.order b{color:#f2f6fc}.order small{display:block;color:#8399b5;margin-top:7px}.order a{color:#71d8c0}.actions{display:flex;flex-wrap:wrap;gap:5px}.actions button{font-size:11px;padding:8px;margin:2px}.hidden{display:none!important}#message{color:#72dfc2;line-height:1.8;min-height:24px}footer{text-align:center;padding:15px 0 25px}@media(max-width:420px){.shell{padding:16px 12px}.panel{padding:16px}.row{align-items:flex-start;flex-wrap:wrap}}";

import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import bcrypt from "npm:bcryptjs@2.4.3";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const projectUrl = Deno.env.get("SUPABASE_URL") || "";
const secretKeys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || secretKeys.default || "";
if (!projectUrl || !serviceKey) throw new Error("Supabase server credentials are unavailable.");
const supabase = createClient(projectUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
const bucket = "receipts";
const BASE = "/functions/v1/tabligh-yar";
const limits = new Map();

function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...extra } });
}
function b64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function fromB64url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
async function hmac(keyBytes, value) {
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, typeof value === "string" ? encoder.encode(value) : value));
}
function equalText(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
async function signSession(payload) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const body = b64url(encoder.encode(JSON.stringify({ iss: "tabligh-yar", iat: now, exp: now + 43200, ...payload })));
  const input = header + "." + body;
  return input + "." + b64url(await hmac(encoder.encode(serviceKey), input));
}
async function verifySession(token) {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const input = parts[0] + "." + parts[1];
    const expected = b64url(await hmac(encoder.encode(serviceKey), input));
    if (!equalText(expected, parts[2])) return null;
    const claims = JSON.parse(decoder.decode(fromB64url(parts[1])));
    if (claims.iss !== "tabligh-yar" || !Number.isInteger(claims.exp) || claims.exp <= Math.floor(Date.now() / 1000)) return null;
    return claims;
  } catch { return null; }
}
function cookie(req, name) {
  const raw = req.headers.get("cookie") || "";
  for (const part of raw.split(";")) {
    const index = part.indexOf("=");
    if (index > -1 && part.slice(0, index).trim() === name) return decodeURIComponent(part.slice(index + 1).trim());
  }
  return "";
}
function csrfCookie(token) {
  return "ty_csrf=" + encodeURIComponent(token) + "; Path=/; Max-Age=43200; Secure; SameSite=None";
}
function sessionCookie(token) {
  return "ty_session=" + encodeURIComponent(token) + "; Path=/; Max-Age=43200; HttpOnly; Secure; SameSite=None";
}
function clearSessionCookie() {
  return "ty_session=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=None";
}
function checkCsrf(req) {
  return equalText(cookie(req, "ty_csrf"), req.headers.get("x-csrf-token") || "");
}
async function requireSession(req, role) {
  const claims = await verifySession(cookie(req, "ty_session"));
  if (!claims || (role && claims.role !== role)) return null;
  return claims;
}
function limited(req, name, max, windowMs) {
  const ip = (req.headers.get("x-forwarded-for") || "unknown").split(",")[0].trim();
  const key = name + ":" + ip;
  const now = Date.now();
  let entry = limits.get(key);
  if (!entry || entry.until <= now) entry = { count: 0, until: now + windowMs };
  entry.count++;
  limits.set(key, entry);
  if (limits.size > 4000) for (const [k, v] of limits) if (v.until <= now) limits.delete(k);
  return entry.count <= max;
}
function isChannelUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === "https:" && (u.hostname === "eitaa.com" || u.hostname === "www.eitaa.com") && !u.username && !u.password && !u.port;
  } catch { return false; }
}
function detectImage(bytes) {
  if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return ".jpg";
  const png = [137,80,78,71,13,10,26,10];
  if (bytes.length >= 8 && png.every((v, i) => bytes[i] === v)) return ".png";
  if (bytes.length >= 12 && decoder.decode(bytes.slice(0,4)) === "RIFF" && decoder.decode(bytes.slice(8,12)) === "WEBP") return ".webp";
  return null;
}
function safeHttpsUrl(value) {
  try { return new URL(value).protocol === "https:"; } catch { return false; }
}
async function verifyEitaaInitData(initData) {
  const botToken = Deno.env.get("EITAA_BOT_TOKEN") || "";
  if (!botToken || typeof initData !== "string" || !initData) return null;
  const params = new URLSearchParams(initData);
  const entries = [...params.entries()];
  const keys = entries.map(([key]) => key);
  if (new Set(keys).size !== keys.length) return null;
  const hash = params.get("hash");
  const authDate = Number(params.get("auth_date"));
  const now = Math.floor(Date.now() / 1000);
  if (!hash || !/^[a-f0-9]{64}$/i.test(hash) || !Number.isInteger(authDate) || authDate > now + 30 || now - authDate > 3600) return null;
  params.delete("hash");
  const checkString = [...params.entries()].map(([key, value]) => key + "=" + value).sort().join("\n");
  const secret = await hmac(encoder.encode("WebAppData"), botToken);
  const calculated = [...await hmac(secret, checkString)].map(v => v.toString(16).padStart(2,"0")).join("");
  if (!equalText(calculated.toLowerCase(), hash.toLowerCase())) return null;
  try {
    const user = JSON.parse(params.get("user") || "{}");
    if (!user.id) return null;
    return { id: String(user.id), name: [user.first_name, user.last_name].filter(Boolean).join(" ") || user.username || "کاربر ایتا" };
  } catch { return null; }
}
async function storeReceipt(file) {
  if (!(file instanceof File) || file.size < 1 || file.size > 5 * 1024 * 1024) throw Object.assign(new Error("حجم رسید باید حداکثر ۵ مگابایت باشد."), { status: 400 });
  const ext = detectImage(new Uint8Array(await file.arrayBuffer()));
  const expected = { "image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp" }[file.type];
  if (!ext || ext !== expected) throw Object.assign(new Error("محتوای فایل با فرمت تصویر انتخاب‌شده مطابقت ندارد."), { status: 400 });
  const key = crypto.randomUUID() + ext;
  const bytes = new Uint8Array(await file.arrayBuffer());
  const { error } = await supabase.storage.from(bucket).upload(key, bytes, { contentType: file.type, cacheControl: "0", upsert: false });
  if (error) throw Object.assign(new Error("ذخیره رسید انجام نشد."), { status: 503 });
  return key;
}
async function deleteReceipt(key) {
  if (key) await supabase.storage.from(bucket).remove([key]).catch(() => {});
}
async function ensureAdmin() {
  const username = Deno.env.get("ADMIN_USERNAME") || "";
  const password = Deno.env.get("ADMIN_PASSWORD") || "";
  if (!username || !password) throw Object.assign(new Error("اطلاعات مدیر در تنظیمات محرمانه سرور ثبت نشده است."), { status: 503 });
  const hash = await bcrypt.hash(password, 12);
  const { error } = await supabase.from("admins").upsert({ username, password_hash: hash }, { onConflict: "username", ignoreDuplicates: true });
  if (error) throw Object.assign(new Error("راه‌اندازی حساب مدیر انجام نشد."), { status: 503 });
  return { username, password };
}
const headers = {
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Content-Security-Policy": "default-src 'self'; script-src 'self' https://developer.eitaa.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self' https://eitaa.com https://*.eitaa.com",
};
function pageResponse(body, type, cache = "no-cache") {
  return new Response(body, { headers: { ...headers, "Content-Type": type, "Cache-Control": cache } });
}
Deno.serve(async (req) => {
  const url = new URL(req.url);
  const path = url.pathname.startsWith(BASE) ? (url.pathname.slice(BASE.length) || "/") : url.pathname;
  if (req.method === "GET" && path === "/") return pageResponse(HTML.replace('href="/styles.css"', 'href="' + BASE + '/styles.css"').replace('<script src="/app.js"></script>', '<script src="' + BASE + '/app.js"></script>'), "text/html; charset=utf-8", "no-store");
  if (req.method === "GET" && path === "/app.js") return pageResponse(APP_JS, "application/javascript; charset=utf-8");
  if (req.method === "GET" && path === "/styles.css") return pageResponse(CSS, "text/css; charset=utf-8");
  if (req.method === "GET" && path === "/api/health") {
    const { error } = await supabase.from("app_settings").select("id").eq("id", 1).maybeSingle();
    return error ? json({ ok: false, database: "unavailable" }, 503) : json({ ok: true, database: "connected" });
  }
  if (req.method === "GET" && path === "/api/csrf") {
    const token = Array.from(crypto.getRandomValues(new Uint8Array(24))).map(v => v.toString(16).padStart(2,"0")).join("");
    return json({ ok: true, csrfToken: token }, 200, { "Set-Cookie": csrfCookie(token) });
  }
  if (req.method === "GET" && path === "/api/config") {
    const { data, error } = await supabase.from("app_settings").select("price_per_10000,min_views,max_views,payment_instructions").eq("id", 1).maybeSingle();
    if (error || !data) return json({ error: "تنظیمات اولیه پایگاه داده موجود نیست." }, 503);
    return json(data);
  }
  if (req.method === "POST" && path === "/api/auth/eitaa") {
    if (!limited(req, "eitaa-auth", 12, 900000)) return json({ error: "تعداد تلاش‌ها زیاد است؛ کمی بعد دوباره امتحان کنید." }, 429);
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است؛ صفحه را تازه‌سازی کنید." }, 403);
    const body = await req.json().catch(() => ({}));
    const user = await verifyEitaaInitData(body.initData);
    if (!user) return json({ error: Deno.env.get("EITAA_BOT_TOKEN") ? "اطلاعات ورود ایتا معتبر نیست." : "توکن ایتا هنوز در تنظیمات سرور ثبت نشده است." }, Deno.env.get("EITAA_BOT_TOKEN") ? 401 : 503);
    const token = await signSession({ role: "customer", ...user });
    return json({ ok: true, user }, 200, { "Set-Cookie": sessionCookie(token) });
  }
  if (req.method === "POST" && path === "/api/auth/admin") {
    if (!limited(req, "admin-auth", 12, 900000)) return json({ error: "تعداد تلاش‌ها زیاد است؛ کمی بعد دوباره امتحان کنید." }, 429);
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است؛ صفحه را تازه‌سازی کنید." }, 403);
    try {
      const body = await req.json();
      const { username } = await ensureAdmin();
      const { data, error } = await supabase.from("admins").select("username,password_hash").eq("username", username).maybeSingle();
      if (error || !data || body.username !== username || typeof body.password !== "string" || !await bcrypt.compare(body.password, data.password_hash)) return json({ error: "نام کاربری یا رمز عبور اشتباه است." }, 401);
      const token = await signSession({ role: "admin", username });
      return json({ ok: true }, 200, { "Set-Cookie": sessionCookie(token) });
    } catch (error) { return json({ error: error.message || "خطای ورود مدیر." }, error.status || 503); }
  }
  if (req.method === "POST" && path === "/api/auth/logout") {
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است." }, 403);
    return json({ ok: true }, 200, { "Set-Cookie": clearSessionCookie() });
  }
  if (req.method === "POST" && path === "/api/orders") {
    if (!limited(req, "create-order", 15, 3600000)) return json({ error: "سقف ثبت سفارش موقتاً پر شده است." }, 429);
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است؛ صفحه را تازه‌سازی کنید." }, 403);
    const session = await requireSession(req, "customer");
    if (!session) return json({ error: "ابتدا از داخل ایتا وارد شوید." }, 401);
    let receiptKey = null;
    let insertedId = null;
    try {
      const form = await req.formData();
      const views = Number(form.get("views"));
      const channelUrl = String(form.get("channelUrl") || "").trim();
      const adText = String(form.get("adText") || "").trim();
      const file = form.get("receipt");
      if (!(file instanceof File)) throw Object.assign(new Error("تصویر رسید الزامی است."), { status: 400 });
      if (!Number.isInteger(views) || views < 1000 || views > 200000 || views % 1000) throw Object.assign(new Error("تعداد بازدید باید بین ۱۰۰۰ تا ۲۰۰۰۰۰ و مضرب ۱۰۰۰ باشد."), { status: 400 });
      if (!isChannelUrl(channelUrl)) throw Object.assign(new Error("لینک کانال باید از دامنه رسمی ایتا باشد."), { status: 400 });
      if (adText.length < 5 || adText.length > 5000) throw Object.assign(new Error("متن تبلیغ باید بین ۵ تا ۵۰۰۰ نویسه باشد."), { status: 400 });
      const { data: settings, error: settingsError } = await supabase.from("app_settings").select("price_per_10000,min_views,max_views,payment_instructions").eq("id",1).single();
      if (settingsError) throw Object.assign(new Error("خواندن تعرفه انجام نشد."), { status: 503 });
      if (views < settings.min_views || views > settings.max_views) throw Object.assign(new Error("تعداد بازدید خارج از محدوده تنظیم‌شده است."), { status: 400 });
      if (!String(settings.payment_instructions || "").trim()) throw Object.assign(new Error("اطلاعات پرداخت هنوز توسط مدیر ثبت نشده است؛ فعلاً امکان ثبت سفارش وجود ندارد."), { status: 503 });
      receiptKey = await storeReceipt(file);
      const rate = BigInt(settings.price_per_10000);
      const price = (rate * BigInt(views) + 5000n) / 10000n;
      const { data: order, error: orderError } = await supabase.from("ad_orders").insert({
        user_id: session.id, user_name: session.name || null, channel_url: new URL(channelUrl).toString(),
        ad_text: adText, requested_views: views, quoted_price: price.toString(),
        price_per_10000_snapshot: rate.toString(), receipt_path: receiptKey
      }).select("id,status,requested_views,quoted_price,created_at").single();
      if (orderError) throw Object.assign(new Error("ثبت سفارش در پایگاه داده انجام نشد."), { status: 503 });
      insertedId = order.id;
      const { error: eventError } = await supabase.from("order_events").insert({ order_id: order.id, admin_username: "system", old_status: null, new_status: "pending", note: "سفارش توسط کاربر ثبت شد" });
      if (eventError) throw Object.assign(new Error("ثبت رویداد سفارش انجام نشد."), { status: 503 });
      return json({ order }, 201);
    } catch (error) {
      if (insertedId) await supabase.from("ad_orders").delete().eq("id", insertedId);
      await deleteReceipt(receiptKey);
      return json({ error: error.message || "ثبت سفارش انجام نشد." }, error.status || 500);
    }
  }
  if (req.method === "GET" && path === "/api/orders") {
    const session = await requireSession(req, "customer");
    if (!session) return json({ error: "ابتدا وارد شوید." }, 401);
    const { data, error } = await supabase.from("ad_orders").select("id,requested_views,quoted_price,status,admin_note,published_post_url,final_views,created_at,updated_at").eq("user_id", session.id).order("created_at", { ascending: false }).limit(100);
    return error ? json({ error: "دریافت سفارش‌ها انجام نشد." }, 503) : json({ orders: data || [] });
  }
  if (req.method === "GET" && path === "/api/admin/orders") {
    const session = await requireSession(req, "admin");
    if (!session) return json({ error: "دسترسی مدیر لازم است." }, 403);
    const { data, error } = await supabase.from("ad_orders").select("id,user_id,user_name,channel_url,ad_text,requested_views,quoted_price,status,admin_note,published_post_url,final_views,created_at").order("created_at", { ascending: false }).limit(300);
    return error ? json({ error: "دریافت سفارش‌ها انجام نشد." }, 503) : json({ orders: data || [] });
  }
  const receiptMatch = path.match(/^\/api\/admin\/orders\/(\d+)\/receipt$/);
  if (req.method === "GET" && receiptMatch) {
    const session = await requireSession(req, "admin");
    if (!session) return json({ error: "دسترسی مدیر لازم است." }, 403);
    const { data: order, error } = await supabase.from("ad_orders").select("receipt_path").eq("id", Number(receiptMatch[1])).maybeSingle();
    if (error || !order || !/^[0-9a-f-]{36}\.(jpg|png|webp)$/i.test(order.receipt_path)) return new Response("Not found", { status: 404 });
    const { data: file, error: downloadError } = await supabase.storage.from(bucket).download(order.receipt_path);
    if (downloadError || !file) return new Response("Not found", { status: 404 });
    const type = order.receipt_path.endsWith(".png") ? "image/png" : order.receipt_path.endsWith(".webp") ? "image/webp" : "image/jpeg";
    return new Response(file.stream(), { headers: { ...headers, "Content-Type": type, "Cache-Control": "private, no-store" } });
  }
  const orderMatch = path.match(/^\/api\/admin\/orders\/(\d+)$/);
  if (req.method === "PATCH" && orderMatch) {
    const session = await requireSession(req, "admin");
    if (!session) return json({ error: "دسترسی مدیر لازم است." }, 403);
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است." }, 403);
    const body = await req.json().catch(() => ({}));
    const allowed = ["pending","paid","payment_rejected","published","completed","cancelled"];
    if (!allowed.includes(body.status)) return json({ error: "وضعیت سفارش نامعتبر است." }, 400);
    if (body.publishedPostUrl && !safeHttpsUrl(body.publishedPostUrl)) return json({ error: "لینک انتشار باید HTTPS باشد." }, 400);
    if (body.finalViews != null && (!Number.isSafeInteger(body.finalViews) || body.finalViews < 0)) return json({ error: "تعداد بازدید نهایی نامعتبر است." }, 400);
    const id = Number(orderMatch[1]);
    const { data: old, error: oldError } = await supabase.from("ad_orders").select("status,admin_note,published_post_url,final_views").eq("id",id).maybeSingle();
    if (oldError || !old) return json({ error: "سفارش پیدا نشد." }, 404);
    const patch = {
      status: body.status,
      admin_note: typeof body.note === "string" ? body.note.slice(0,1000) : old.admin_note,
      published_post_url: body.publishedPostUrl || old.published_post_url,
      final_views: body.finalViews == null ? old.final_views : body.finalViews,
      updated_at: new Date().toISOString()
    };
    const { data: updated, error: updateError } = await supabase.from("ad_orders").update(patch).eq("id",id).select("id,status,admin_note,published_post_url,final_views").single();
    if (updateError) return json({ error: "به‌روزرسانی سفارش انجام نشد." }, 503);
    const { error: eventError } = await supabase.from("order_events").insert({ order_id:id, admin_username:session.username, old_status:old.status, new_status:body.status, note:patch.admin_note });
    if (eventError) return json({ error: "وضعیت تغییر کرد، اما ثبت سابقه انجام نشد؛ مدیر باید سابقه را بررسی کند.", order:updated }, 503);
    return json({ order:updated });
  }
  if (req.method === "PATCH" && path === "/api/admin/settings") {
    const session = await requireSession(req, "admin");
    if (!session) return json({ error: "دسترسی مدیر لازم است." }, 403);
    if (!checkCsrf(req)) return json({ error: "درخواست نامعتبر است." }, 403);
    const body = await req.json().catch(() => ({}));
    const rate = Number(body.pricePer10000);
    const paymentInstructions = typeof body.paymentInstructions === "string" ? body.paymentInstructions.trim() : "";
    if (!Number.isSafeInteger(rate) || rate < 1 || rate > 1000000000) return json({ error: "تعرفه نامعتبر است." }, 400);
    if (paymentInstructions.length > 2000) return json({ error: "اطلاعات پرداخت حداکثر ۲۰۰۰ نویسه است." }, 400);
    const { data, error } = await supabase.from("app_settings").update({ price_per_10000:rate, payment_instructions:paymentInstructions, updated_at:new Date().toISOString() }).eq("id",1).select("price_per_10000,min_views,max_views,payment_instructions").single();
    return error ? json({ error: "ذخیره تنظیمات انجام نشد." }, 503) : json({ settings:data });
  }
  return json({ error: "مسیر پیدا نشد." }, 404);
});
