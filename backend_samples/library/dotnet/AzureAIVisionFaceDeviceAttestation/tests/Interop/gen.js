// Cross-language crypto vector generator for the .NET port.
//
// Reproduces the exact primitives from the npm library's crypto_utils.ts (pure
// Node `crypto` calls) so we can prove the C# port interoperates byte-for-byte
// with the same mobile clients.
//
//   node gen.js gen                      -> prints a JSON vector on stdout
//   node gen.js decrypt <blobFile> <pem> -> decrypts a C#-produced Tink blob
'use strict';
const crypto = require('crypto');

function generateServerKeyPairEC() {
  return crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
}

function encryptWithPublicKeyEC(data, publicKeyPem) {
  const ephemeral = generateServerKeyPairEC();
  const ephemeralPrivateKeyObj = crypto.createPrivateKey(ephemeral.privateKey);
  const recipientPublicKeyObj = crypto.createPublicKey(publicKeyPem);
  const ephemeralPublicKeyObj = crypto.createPublicKey(ephemeral.publicKey);
  const ephemeralPublicKeyRaw = ephemeralPublicKeyObj.export({ type: 'spki', format: 'der' });
  const ephemeralPoint = ephemeralPublicKeyRaw.subarray(-65);
  const sharedSecret = crypto.diffieHellman({ privateKey: ephemeralPrivateKeyObj, publicKey: recipientPublicKeyObj });
  const ikm = Buffer.concat([ephemeralPoint, sharedSecret]);
  const aesKey = Buffer.from(crypto.hkdfSync('sha256', ikm, Buffer.alloc(0), Buffer.alloc(0), 32));
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', aesKey, iv);
  const encrypted = Buffer.concat([cipher.update(data, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return Buffer.concat([ephemeralPoint, iv, encrypted, authTag]).toString('base64');
}

function decryptWithPrivateKeyEC(tinkCiphertext, privateKeyPem) {
  const tinkBlob = Buffer.from(tinkCiphertext, 'base64');
  const ephemeralPoint = tinkBlob.subarray(0, 65);
  const iv = tinkBlob.subarray(65, 77);
  const ciphertextWithTag = tinkBlob.subarray(77);
  const authTag = ciphertextWithTag.subarray(-16);
  const ciphertext = ciphertextWithTag.subarray(0, -16);
  const p256Oid = Buffer.from([0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07]);
  const ecPublicKeyOid = Buffer.from([0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01]);
  const algorithmIdentifier = Buffer.concat([Buffer.from([0x30, ecPublicKeyOid.length + p256Oid.length]), ecPublicKeyOid, p256Oid]);
  const publicKeyBitString = Buffer.concat([Buffer.from([0x03, ephemeralPoint.length + 1, 0x00]), ephemeralPoint]);
  const spkiDer = Buffer.concat([Buffer.from([0x30, algorithmIdentifier.length + publicKeyBitString.length]), algorithmIdentifier, publicKeyBitString]);
  const privateKeyObj = crypto.createPrivateKey(privateKeyPem);
  const ephemeralPublicKeyObj = crypto.createPublicKey({ key: spkiDer, format: 'der', type: 'spki' });
  const sharedSecret = crypto.diffieHellman({ privateKey: privateKeyObj, publicKey: ephemeralPublicKeyObj });
  const ikm = Buffer.concat([ephemeralPoint, sharedSecret]);
  const aesKey = Buffer.from(crypto.hkdfSync('sha256', ikm, Buffer.alloc(0), Buffer.alloc(0), 32));
  const decipher = crypto.createDecipheriv('aes-256-gcm', aesKey, iv);
  decipher.setAuthTag(authTag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

const mode = process.argv[2] || 'gen';
if (mode === 'gen') {
  const auth = generateServerKeyPairEC();
  const signedData = 'attestation-cross-language-vector-v1';
  const signature = crypto.sign('sha256', Buffer.from(signedData, 'utf8'), { key: auth.privateKey, dsaEncoding: 'der' }).toString('base64');
  const recipient = generateServerKeyPairEC();
  const plaintext = 'hello-attestation-\uD83D\uDD12-token{"t":1}';
  const tinkCiphertext = encryptWithPublicKeyEC(plaintext, recipient.publicKey);
  process.stdout.write(JSON.stringify({
    authPublicKey: auth.publicKey,
    signedData,
    signature,
    recipientPublicKey: recipient.publicKey,
    recipientPrivateKey: recipient.privateKey,
    plaintext,
    tinkCiphertext,
  }, null, 2));
} else if (mode === 'decrypt') {
  const fs = require('fs');
  const blob = fs.readFileSync(process.argv[3], 'utf8').trim();
  const priv = fs.readFileSync(process.argv[4], 'utf8');
  process.stdout.write(decryptWithPrivateKeyEC(blob, priv));
}
