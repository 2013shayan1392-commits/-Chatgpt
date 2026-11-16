import { createHmac, timingSafeEqual } from 'node:crypto';

export function isEitaaChannelUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' &&
      (url.hostname === 'eitaa.com' || url.hostname === 'www.eitaa.com') &&
      !url.username && !url.password && !url.port;
  } catch { return false; }
}

export function detectImageExtension(input) {
  const data = Buffer.isBuffer(input) ? input : Buffer.from(input || []);
  if (data.length >= 3 && data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff) return '.jpg';
  const png = [0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a];
  if (data.length >= 8 && png.every((byte, index) => data[index] === byte)) return '.png';
  if (data.length >= 12 && data.toString('ascii', 0, 4) === 'RIFF' && data.toString('ascii', 8, 12) === 'WEBP') return '.webp';
  return null;
}

export function calculatePrice(pricePer10000, views) {
  const rate = BigInt(pricePer10000);
  const count = BigInt(views);
  if (rate <= 0n || count <= 0n) throw new RangeError('rate and views must be positive');
  // Round to the nearest whole toman, consistently with the browser display.
  return (rate * count + 5000n) / 10000n;
}

export function verifyEitaaInitData(initData, botToken, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (!botToken || typeof initData !== 'string' || !initData) return null;
  const params = new URLSearchParams(initData);
  const entries = [...params.entries()];
  const keys = entries.map(([key]) => key);
  if (new Set(keys).size !== keys.length) return null;
  const hash = params.get('hash');
  const authDate = Number(params.get('auth_date'));
  if (!hash || !/^[a-f0-9]{64}$/i.test(hash) || !Number.isInteger(authDate) ||
      authDate > nowSeconds + 30 || nowSeconds - authDate > 3600) return null;
  params.delete('hash');
  const checkString = [...params.entries()].map(([key, value]) => key + '=' + value).sort().join('\n');
  const secretKey = createHmac('sha256', 'WebAppData').update(botToken).digest();
  const calculated = createHmac('sha256', secretKey).update(checkString).digest('hex');
  const a = Buffer.from(calculated, 'hex'), b = Buffer.from(hash, 'hex');
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
  try {
    const user = JSON.parse(params.get('user') || '{}');
    if (!user.id) return null;
    return { id: String(user.id), name: [user.first_name, user.last_name].filter(Boolean).join(' ') || user.username || 'کاربر ایتا' };
  } catch { return null; }
}
