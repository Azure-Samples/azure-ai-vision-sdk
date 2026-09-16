import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign, webcrypto, X509Certificate } from 'node:crypto';
import { test } from 'node:test';
import { BitString, Integer, ObjectIdentifier, Utf8String } from 'asn1js';
import { AttributeTypeAndValue, BasicConstraints, Certificate, CryptoEngine, Extension } from 'pkijs';
import { computeCertThumbprint, getExtensionValue, pemToDer, validateCertificatePath } from '../src/cert_utils';
import { NONCE_OID } from '../src/ios/constants';
import { extractNonceFromCredCert } from '../src/ios/parsers';
import { checkCertificateRevocation } from '../src/android/revocation';
import {
  KEYMASTER_EXT_OID, extractAttestationChallengeFromCert,
  isHardwareAttestationSecurityLevel, parseKeyDescription,
} from '../src/android/keymaster_ext';

function tlv(tag: number, value: Buffer): Buffer {
  const length = value.length < 128 ? [value.length]
    : value.length < 256 ? [0x81, value.length] : [0x82, value.length >> 8, value.length & 0xff];
  return Buffer.concat([Buffer.from([tag, ...length]), value]);
}

function extension(oid: Buffer, payload: Buffer, critical = false): Buffer {
  return tlv(0x30, Buffer.concat([
    oid, Buffer.from(critical ? [0x01, 0x01, 0xff] : []), tlv(0x04, payload),
  ]));
}

function certificate(...extensions: Buffer[]): Buffer {
  return certificateWithSerial(1, extensions);
}

function certificateWithSerial(serialNumber: number, extensions: Buffer[] = []): Buffer {
  const keys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const algorithm = Buffer.from('300a06082a8648ce3d040302', 'hex');
  const name = tlv(0x30, tlv(0x31, tlv(0x30, Buffer.concat([
    Buffer.from('0603550403', 'hex'), tlv(0x0c, Buffer.from('oid-test')),
  ]))));
  const validity = tlv(0x30, Buffer.concat([
    tlv(0x17, Buffer.from('250101000000Z')), tlv(0x17, Buffer.from('350101000000Z')),
  ]));
  const tbs = tlv(0x30, Buffer.concat([
    Buffer.from('a003020102', 'hex'), Buffer.from(new Integer({ value: serialNumber }).toBER()),
    algorithm, name, validity, name,
    keys.publicKey.export({ type: 'spki', format: 'der' }),
    tlv(0xa3, tlv(0x30, Buffer.concat(extensions))),
  ]));
  return tlv(0x30, Buffer.concat([
    tbs, algorithm, tlv(0x03, Buffer.concat([Buffer.from([0]), sign('sha256', tbs, keys.privateKey)])),
  ]));
}

const nonce = Buffer.alloc(32, 0xab);
test('Android hardware attestation security level accepts only TEE or StrongBox', () => {
  const description = {
    attestationVersion: 1,
    keyMintVersion: 2,
    keyMintSecurityLevel: 1,
    attestationChallenge: nonce,
  };
  assert.equal(isHardwareAttestationSecurityLevel(null), false);
  assert.equal(isHardwareAttestationSecurityLevel({ ...description, attestationSecurityLevel: 0 }), false);
  assert.equal(isHardwareAttestationSecurityLevel({ ...description, attestationSecurityLevel: 1 }), true);
  assert.equal(isHardwareAttestationSecurityLevel({ ...description, attestationSecurityLevel: 2 }), true);
  assert.equal(isHardwareAttestationSecurityLevel({ ...description, attestationSecurityLevel: 3 }), false);
});

test('PEM helpers preserve certificate DER and reject malformed or multiple blocks', context => {
  context.mock.method(console, 'error', () => {});
  const der = certificate();
  const pem = new X509Certificate(der).toString();
  assert.deepEqual(pemToDer(pem), der);
  assert.deepEqual(pemToDer(pem.replace(/\r?\n/g, '\r\n')), der);
  assert.equal(computeCertThumbprint(pem), createHash('sha256').update(der).digest('hex'));
  for (const malformed of ['AA==', 'not PEM', `${pem}\n${pem}`, pem.replaceAll('CERTIFICATE', 'PUBLIC KEY'),
    pem.replace('END CERTIFICATE', 'END PUBLIC KEY'),
    '-----BEGIN CERTIFICATE-----\n!\n-----END CERTIFICATE-----',
    '-----BEGIN CERTIFICATE-----\nMAA=\n-----END CERTIFICATE-----']) {
    assert.throws(() => pemToDer(malformed));
    assert.equal(computeCertThumbprint(malformed), null);
  }
});

test('revocation lookups use canonical integer serials without byte padding', context => {
  context.mock.method(console, 'warn', () => {});
  for (const serial of [15, 16, 4095, 128]) {
    const der = certificateWithSerial(serial);
    const result = checkCertificateRevocation(der, {
      entries: { [serial.toString(16)]: { status: 'REVOKED', reason: 'KEY_COMPROMISE' } },
    });
    assert.equal(result.isRevoked, true, `serial ${serial.toString(16)}`);
    assert.equal(checkCertificateRevocation(der, { entries: {} }).isRevoked, false);
  }
});

test('certificate paths enforce pinned roots, constraints, dates, signatures and exact order', async context => {
  const fetchMock = context.mock.method(globalThis, 'fetch', () => { throw new Error('Certificate validation must not fetch'); });
  const engine = new CryptoEngine({
    name: 'node', crypto: webcrypto as unknown as ConstructorParameters<typeof CryptoEngine>[0]['crypto'],
  });
  const keys = await Promise.all(['root', 'issuer', 'leaf'].map(() => engine.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'],
  )));
  for (const scenario of ['valid', 'untrusted', 'non-ca', 'key-usage', 'path-length', 'expired',
    'future', 'expired-root', 'critical-extension', 'signature', 'missing', 'reordered', 'extra']) {
    const now = Date.now();
    const makeCertificate = async (index: number, issuerIndex: number): Promise<Buffer> => {
      const cert = new Certificate({ version: 2, serialNumber: new Integer({ value: index + 1 }) });
      cert.subject.typesAndValues.push(new AttributeTypeAndValue({ type: '2.5.4.3', value: new Utf8String({ value: `Path ${index}` }) }));
      cert.issuer.typesAndValues.push(new AttributeTypeAndValue({ type: '2.5.4.3', value: new Utf8String({ value: `Path ${issuerIndex}` }) }));
      cert.notBefore.value = new Date(now + (scenario === 'future' && index === 2 ? 3600000 : -86400000));
      cert.notAfter.value = new Date(now + ((scenario === 'expired' && index === 2) ||
        (scenario === 'expired-root' && index === 0) ? -3600000 : 86400000));
      cert.extensions = [];
      if (index < 2) {
        const constraints = new BasicConstraints({ cA: !(scenario === 'non-ca' && index === 1),
          ...(index === 0 ? { pathLenConstraint: scenario === 'path-length' ? 0 : 1 } : {}) });
        cert.extensions.push(new Extension({ extnID: '2.5.29.19', critical: true, extnValue: constraints.toSchema().toBER(false) }));
        const usage = new BitString({ valueHex: new Uint8Array([scenario === 'key-usage' && index === 1 ? 0x80 : 0x04]).buffer, unusedBits: scenario === 'key-usage' && index === 1 ? 7 : 2 });
        cert.extensions.push(new Extension({ extnID: '2.5.29.15', critical: true, extnValue: usage.toBER(false) }));
      }
      if (index === 2 && scenario === 'critical-extension') {
        cert.extensions.push(new Extension({ extnID: '1.2.3.4', critical: true, extnValue: new Uint8Array([5, 0]).buffer }));
      }
      await cert.subjectPublicKeyInfo.importKey(keys[index].publicKey, engine);
      await cert.sign(keys[issuerIndex].privateKey, 'SHA-256', engine);
      return Buffer.from(cert.toSchema(true).toBER(false));
    };
    const root = await makeCertificate(0, 0);
    const issuer = await makeCertificate(1, 0);
    const leaf = await makeCertificate(2, 1);
    const chain = [leaf, issuer, root];
    if (scenario === 'signature') leaf[leaf.length - 1] ^= 1;
    if (scenario === 'missing') chain.splice(1, 1);
    if (scenario === 'reordered') [chain[0], chain[1]] = [chain[1], chain[0]];
    if (scenario === 'extra') chain.splice(1, 0, root);
    const pins = scenario === 'untrusted' ? [] : [`-----BEGIN CERTIFICATE-----\n${root.toString('base64')}\n-----END CERTIFICATE-----`];
    assert.equal(await validateCertificatePath(chain, pins), scenario === 'valid', scenario);
    assert.equal(await validateCertificatePath([root], pins), false);
    assert.equal(await validateCertificatePath([Buffer.from([0x30]), root], pins), false);
    assert.equal(await validateCertificatePath([Buffer.concat([leaf, Buffer.from([0])]), issuer, root], pins), false);
  }
  assert.equal(fetchMock.mock.callCount(), 0);
});

const cases = [
  {
    platform: 'Apple', oid: NONCE_OID,
    payload: tlv(0x30, tlv(0xa1, tlv(0x04, nonce))), extract: extractNonceFromCredCert,
  },
  {
    platform: 'Android', oid: KEYMASTER_EXT_OID,
    payload: tlv(0x30, Buffer.concat([
      Buffer.from('0201010a01010201020a0101', 'hex'), tlv(0x04, nonce), Buffer.from('040030003000', 'hex'),
    ])), extract: extractAttestationChallengeFromCert,
  },
];

for (const { platform, oid, payload, extract } of cases) {
  const oidDer = Buffer.from(new ObjectIdentifier({ value: oid }).toBER());
  test(`${platform} rejects malformed ASN.1 payloads`, () => {
    const wrongTag = Buffer.from(payload);
    wrongTag[2] = 0x05;
    const nestedOrNegative = platform === 'Apple'
      ? tlv(0x30, tlv(0xa1, Buffer.concat([tlv(0x04, nonce), Buffer.from('0500', 'hex')])))
      : Buffer.from(payload);
    if (platform === 'Android') nestedOrNegative[4] = 0xff;
    for (const malformed of [
      Buffer.alloc(0), Buffer.from([0x30]), payload.subarray(0, -1), wrongTag,
      Buffer.concat([payload, Buffer.from('0500', 'hex')]), nestedOrNegative,
    ]) {
      assert.equal(extract(certificate(extension(oidDer, malformed))), null);
    }
  });
  for (const critical of [false, true]) {
    test(`${platform} structural lookup, critical=${critical}`, () => {
      const fakePayload = Buffer.from(payload);
      fakePayload.fill(0, fakePayload.indexOf(nonce), fakePayload.indexOf(nonce) + nonce.length);
      const decoy = extension(Buffer.from('06032a0304', 'hex'), Buffer.concat([oidDer, tlv(0x04, fakePayload)]));
      const missing = certificate(decoy);
      assert.equal(getExtensionValue(missing, oid), null);
      assert.equal(extract(missing), null);
      assert.equal(extract(Buffer.from([0x30, 0])), null);
      const real = extension(oidDer, payload, critical);
      const cert = certificate(decoy, real);
      const parsed = new X509Certificate(cert);
      assert.equal(parsed.verify(parsed.publicKey), true);
      assert.deepEqual(getExtensionValue(cert, oid), payload);
      assert.deepEqual(extract(cert), nonce);
      if (platform === 'Android') {
        assert.deepEqual(parseKeyDescription(cert), {
          attestationVersion: 1, attestationSecurityLevel: 1, keyMintVersion: 2,
          keyMintSecurityLevel: 1, attestationChallenge: nonce,
        });
      }
      assert.equal(extract(certificate(real, real)), null);
      const longPayload = Buffer.alloc(300);
      assert.deepEqual(getExtensionValue(certificate(extension(oidDer, longPayload, critical)), oid), longPayload);
    });
  }
}