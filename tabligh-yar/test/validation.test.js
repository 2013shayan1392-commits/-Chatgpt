import test from 'node:test';
import assert from 'node:assert/strict';
import { isEitaaChannelUrl, detectImageExtension, calculatePrice } from '../server/validation.js';

test('accepts only HTTPS links on official Eitaa hostnames', () => {
  assert.equal(isEitaaChannelUrl('https://eitaa.com/mychannel'), true);
  assert.equal(isEitaaChannelUrl('https://www.eitaa.com/mychannel'), true);
  assert.equal(isEitaaChannelUrl('http://eitaa.com/mychannel'), false);
  assert.equal(isEitaaChannelUrl('https://eitaa.com.example.org/mychannel'), false);
  assert.equal(isEitaaChannelUrl('https://eitaa.com@evil.example/mychannel'), false);
  assert.equal(isEitaaChannelUrl('javascript:alert(1)'), false);
});

test('detects image type from file signature, not the supplied filename', () => {
  assert.equal(detectImageExtension(Buffer.from([0xff,0xd8,0xff,0x00])), '.jpg');
  assert.equal(detectImageExtension(Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])), '.png');
  assert.equal(detectImageExtension(Buffer.from('RIFF0000WEBP')), '.webp');
  assert.equal(detectImageExtension(Buffer.from('not an image')), null);
});

test('calculates the quoted price using integer arithmetic and rounding', () => {
  assert.equal(calculatePrice(90000, 10000), 90000n);
  assert.equal(calculatePrice(90000, 1000), 9000n);
  assert.equal(calculatePrice(90001, 1000), 9000n);
  assert.equal(calculatePrice(90005, 1000), 9001n);
  assert.throws(() => calculatePrice(0, 1000), RangeError);
});

import { createHmac } from 'node:crypto';
import { verifyEitaaInitData } from '../server/validation.js';

function signedInitData(fields, token) {
  const params = new URLSearchParams(fields);
  const checkString = [...params.entries()].map(([key, value]) => key + '=' + value).sort().join('\n');
  const secretKey = createHmac('sha256', 'WebAppData').update(token).digest();
  const hash = createHmac('sha256', secretKey).update(checkString).digest('hex');
  params.set('hash', hash);
  return params.toString();
}

test('validates signed Eitaa initData and returns only verified user data', () => {
  const now = 2000000000;
  const token = 'test-bot-token';
  const raw = signedInitData([
    ['auth_date', String(now - 60)],
    ['query_id', 'session-123'],
    ['user', JSON.stringify({id: 1234567890123, first_name: 'Shayan'})]
  ], token);
  assert.deepEqual(verifyEitaaInitData(raw, token, now), {id:'1234567890123',name:'Shayan'});
  assert.equal(verifyEitaaInitData(raw + '&extra=modified', token, now), null);
  assert.equal(verifyEitaaInitData(raw, '', now), null);
});

test('rejects expired, future-dated and duplicate initData parameters', () => {
  const now = 2000000000;
  const token = 'test-bot-token';
  const expired = signedInitData([['auth_date',String(now-3601)],['user','{"id":1}']],token);
  const future = signedInitData([['auth_date',String(now+31)],['user','{"id":1}']],token);
  const valid = signedInitData([['auth_date',String(now-1)],['user','{"id":1}']],token);
  assert.equal(verifyEitaaInitData(expired,token,now),null);
  assert.equal(verifyEitaaInitData(future,token,now),null);
  assert.equal(verifyEitaaInitData(valid+'&auth_date='+String(now-1),token,now),null);
});
