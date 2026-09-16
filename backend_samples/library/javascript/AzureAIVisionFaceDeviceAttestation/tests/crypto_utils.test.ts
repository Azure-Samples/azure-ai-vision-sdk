import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { test } from 'node:test';
import { decryptWithPrivateKeyEC, encryptWithPublicKeyEC, generateServerKeyPairEC } from '../src/crypto_utils';

test('ECIES JWK import decrypts an independently encrypted payload with generated keys', () => {
  const recipient = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const recipientJwk = recipient.publicKey.export({ format: 'jwk' });
  assert.ok(recipientJwk.x && recipientJwk.y);
  const recipientPoint = Buffer.concat([
    Buffer.from([0x04]), Buffer.from(recipientJwk.x, 'base64url'), Buffer.from(recipientJwk.y, 'base64url'),
  ]);
  const ephemeral = crypto.createECDH('prime256v1');
  const point = ephemeral.generateKeys();
  const secret = ephemeral.computeSecret(recipientPoint);
  const aesKey = Buffer.from(crypto.hkdfSync('sha256', Buffer.concat([point, secret]), Buffer.alloc(0), Buffer.alloc(0), 32));
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', aesKey, iv);
  const message = 'Independent ECIES compatibility payload';
  const ciphertext = Buffer.concat([cipher.update(message, 'utf8'), cipher.final()]);
  const blob = Buffer.concat([point, iv, ciphertext, cipher.getAuthTag()]);
  const privateKeyPem = recipient.privateKey.export({ format: 'pem', type: 'pkcs8' }).toString();
  assert.equal(decryptWithPrivateKeyEC(blob.toString('base64'), privateKeyPem), message);
});

test('ECIES JWK export preserves uncompressed-point wire format and round trips', () => {
  const keys = generateServerKeyPairEC();
  assert.ok(keys);
  const recipientJwk = crypto.createPrivateKey(keys.privateKey).export({ format: 'jwk' });
  assert.ok(recipientJwk.d);
  const recipient = crypto.createECDH('prime256v1');
  recipient.setPrivateKey(Buffer.from(recipientJwk.d, 'base64url'));
  for (const message of ['', 'ECIES payload', '\u00e9\u4e2d\ud83d\udd10']) {
    const encrypted = encryptWithPublicKeyEC(message, keys.publicKey);
    assert.ok(encrypted);
    const blob = Buffer.from(encrypted, 'base64');
    assert.equal(blob.length, 93 + Buffer.byteLength(message));
    assert.equal(blob[0], 0x04);
    const point = blob.subarray(0, 65);
    const secret = recipient.computeSecret(point);
    const aesKey = Buffer.from(crypto.hkdfSync('sha256', Buffer.concat([point, secret]), Buffer.alloc(0), Buffer.alloc(0), 32));
    const decipher = crypto.createDecipheriv('aes-256-gcm', aesKey, blob.subarray(65, 77));
    decipher.setAuthTag(blob.subarray(-16));
    assert.equal(Buffer.concat([decipher.update(blob.subarray(77, -16)), decipher.final()]).toString('utf8'), message);
    assert.equal(decryptWithPrivateKeyEC(encrypted, keys.privateKey), message);
    assert.equal(decryptWithPrivateKeyEC(blob.toString('base64url'), keys.privateKey), message);
  }
});

test('ECIES JWK import rejects invalid points, tampering, truncation and wrong keys', context => {
  context.mock.method(console, 'error', () => {});
  const keys = generateServerKeyPairEC();
  const otherKeys = generateServerKeyPairEC();
  assert.ok(keys && otherKeys);
  const encrypted = encryptWithPublicKeyEC('payload', keys.publicKey);
  assert.ok(encrypted);
  const blob = Buffer.from(encrypted, 'base64');
  for (const offset of [0, 1, 65, 77, blob.length - 1]) {
    const tampered = Buffer.from(blob);
    tampered[offset] ^= 1;
    assert.equal(decryptWithPrivateKeyEC(tampered.toString('base64'), keys.privateKey), null);
  }
  const offCurve = Buffer.from(blob);
  offCurve.fill(0, 1, 65);
  assert.equal(decryptWithPrivateKeyEC(offCurve.toString('base64'), keys.privateKey), null);
  assert.equal(decryptWithPrivateKeyEC(blob.subarray(0, 92).toString('base64'), keys.privateKey), null);
  assert.equal(decryptWithPrivateKeyEC(encrypted, otherKeys.privateKey), null);
  const wrongCurve = crypto.generateKeyPairSync('ec', { namedCurve: 'secp384r1' });
  assert.equal(encryptWithPublicKeyEC('payload', wrongCurve.publicKey.export({ format: 'pem', type: 'spki' }).toString()), null);
  assert.equal(decryptWithPrivateKeyEC(encrypted, wrongCurve.privateKey.export({ format: 'pem', type: 'pkcs8' }).toString()), null);
});