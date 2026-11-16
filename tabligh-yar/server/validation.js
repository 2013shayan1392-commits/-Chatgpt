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
