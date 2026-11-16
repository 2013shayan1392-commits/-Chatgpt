const $ = id => document.getElementById(id);
let csrfToken = '', settings = { price_per_10000: 90000, min_views: 1000, max_views: 200000 }, eitaaUser = false;
const money = n => Number(n).toLocaleString('fa-IR') + ' تومان';
const escapeHtml = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function api(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.method && options.method !== 'GET') headers['x-csrf-token'] = csrfToken;
  const response = await fetch(url, { ...options, headers, credentials: 'same-origin' });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || 'خطا در ارتباط با سرور');
  return data;
}
async function refreshCsrf() { const d = await api('/api/csrf'); csrfToken = d.csrfToken; }
async function loadSettings() {
  settings = await api('/api/config');
  $('newRate').value = settings.price_per_10000;
  updatePrice();
}
function updatePrice() {
  const views = Number($('views').value) || 0;
  $('price').textContent = money(Math.round(Number(settings.price_per_10000) * views / 10000));
}
async function signInEitaa() {
  const webApp = window.Eitaa?.WebApp;
  if (!webApp?.initData) return false;
  webApp.ready?.(); webApp.expand?.();
  await api('/api/auth/eitaa', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({initData:webApp.initData}) });
  eitaaUser = true;
  return true;
}
function statusName(s) {
  return ({pending:'در انتظار بررسی',paid:'پرداخت تأیید شد',payment_rejected:'رسید رد شد',published:'منتشر شد',completed:'تکمیل شد',cancelled:'لغو شد'})[s] || s;
}
async function loadOrders() {
  try {
    const data = await api('/api/orders');
    $('orders').innerHTML = data.orders.length ? data.orders.map(o => `<article class="order"><b>سفارش #${o.id}</b><br>${Number(o.requested_views).toLocaleString('fa-IR')} بازدید · ${money(o.quoted_price)}<br>وضعیت: ${statusName(o.status)}${o.admin_note ? '<br>یادداشت مدیر: '+escapeHtml(o.admin_note):''}${o.published_post_url ? '<br><a target="_blank" rel="noopener" href="'+escapeHtml(o.published_post_url)+'">لینک انتشار</a>':''}<small>${new Date(o.created_at).toLocaleString('fa-IR')}</small></article>`).join('') : 'هنوز سفارشی ثبت نکرده‌اید.';
  } catch { $('orders').textContent = 'برای مشاهده سفارش‌ها، برنامه را از داخل ایتا باز کنید.'; }
}
$('views').addEventListener('input', updatePrice);
$('orderForm').addEventListener('submit', async e => {
  e.preventDefault(); $('message').textContent = 'در حال ثبت سفارش…';
  try {
    if (!eitaaUser && !(await signInEitaa())) throw new Error('برای ثبت سفارش، مینی‌اپ را از داخل ایتا باز کنید.');
    const file = $('receipt').files[0];
    if (!file) throw new Error('تصویر رسید را انتخاب کنید.');
    const form = new FormData();
    form.append('views', $('views').value); form.append('channelUrl', $('channelUrl').value);
    form.append('adText', $('adText').value); form.append('receipt', file);
    const result = await api('/api/orders', {method:'POST',body:form});
    $('message').textContent = 'سفارش شماره ' + Number(result.order.id).toLocaleString('fa-IR') + ' ثبت شد؛ پس از بررسی رسید، وضعیت آن به‌روزرسانی می‌شود.';
    $('orderForm').reset(); $('views').value = 10000; updatePrice(); await loadOrders();
  } catch(err) { $('message').textContent = err.message; }
});
$('refresh').onclick = loadOrders;
$('adminToggle').onclick = () => $('adminForm').classList.toggle('hidden');
$('adminForm').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    await api('/api/auth/admin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:$('adminUser').value,password:$('adminPass').value})});
    $('adminForm').classList.add('hidden'); $('adminPanel').classList.remove('hidden'); await loadAdminOrders();
  } catch(err) { $('message').textContent = err.message; }
});
async function loadAdminOrders() {
  const data = await api('/api/admin/orders');
  $('adminOrders').innerHTML = data.orders.length ? data.orders.map(o => `<article class="order"><b>#${o.id} · ${escapeHtml(o.user_name || o.user_id)}</b><br>${Number(o.requested_views).toLocaleString('fa-IR')} بازدید · ${money(o.quoted_price)}<br><a href="${escapeHtml(o.channel_url)}" target="_blank" rel="noopener">کانال</a> · <a href="/api/admin/orders/${o.id}/receipt" target="_blank" rel="noopener">مشاهده رسید</a><p>${escapeHtml(o.ad_text)}</p><small>وضعیت: ${statusName(o.status)}</small><div class="actions">${['paid','payment_rejected','published','completed','cancelled'].map(s=>`<button type="button" data-id="${o.id}" data-status="${s}" class="secondary">${statusName(s)}</button>`).join('')}</div></article>`).join('') : 'هنوز سفارشی ثبت نشده است.';
  $('adminOrders').querySelectorAll('button[data-id]').forEach(btn => btn.onclick = async () => {
    const note = prompt('یادداشت مدیر (اختیاری):') || '';
    let publishedPostUrl = null;
    if (btn.dataset.status === 'published') publishedPostUrl = prompt('لینک پست منتشرشده (اختیاری):') || null;
    try { await api('/api/admin/orders/'+btn.dataset.id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:btn.dataset.status,note,publishedPostUrl})}); await loadAdminOrders(); }
    catch(err) { $('message').textContent = err.message; }
  });
}
$('adminRefresh').onclick = () => loadAdminOrders().catch(e => $('message').textContent=e.message);
$('saveRate').onclick = async () => {
  try { const r = await api('/api/admin/settings',{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({pricePer10000:Number($('newRate').value)})}); settings=r.settings; updatePrice(); $('message').textContent='تعرفه جدید ذخیره شد.'; }
  catch(err) { $('message').textContent=err.message; }
};
(async () => {
  try { await refreshCsrf(); await loadSettings(); await signInEitaa(); await loadOrders(); }
  catch(err) { $('message').textContent=err.message; }
})();