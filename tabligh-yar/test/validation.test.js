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
