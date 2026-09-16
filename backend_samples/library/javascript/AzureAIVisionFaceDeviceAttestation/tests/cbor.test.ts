import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decodeCbor } from '../src/ios/cbor';
import { parseAppAttestAssertion, parseAppAttestToken, parseAssertionAuthData } from '../src/ios/parsers';
import { parseAttestAuthData } from '../src/ios/attestation_phases';
import { IosPhaseFail } from '../src/ios/types';
import { encode } from 'cbor';

test('App Attest header preserves unsigned counters and fixed field boundaries', () => {
  for (const counter of [0, 0x01020304, 0x7fffffff, 0x80000000, 0xffffffff]) {
    const authData = Buffer.alloc(38, 0xff);
    authData.fill(0x42, 0, 32);
    authData.writeUInt32BE(counter, 33);
    assert.deepEqual(parseAssertionAuthData(authData), {
      rpIdHash: Buffer.alloc(32, 0x42), flags: 255, signCount: counter,
    });
  }
  assert.throws(() => parseAssertionAuthData(Buffer.alloc(36)), {
    message: 'authenticatorData is 36 bytes, < 37',
  });
});

test('App Attest credential layout preserves lengths and truncation errors', () => {
  for (const length of [0, 1, 255, 256, 32768, 65535]) {
    const authData = Buffer.alloc(55 + length + 1, 0xee);
    authData.fill(0x42, 0, 32);
    authData.fill(0xff, 32, 37);
    authData.fill(0x61, 37, 53);
    authData.writeUInt16BE(length, 53);
    authData.fill(0xab, 55, 55 + length);
    assert.deepEqual(parseAttestAuthData(authData), {
      rpIdHash: Buffer.alloc(32, 0x42), flags: 255, signCount: 4294967295,
      aaguid: Buffer.alloc(16, 0x61), credIdLen: length, credentialId: Buffer.alloc(length, 0xab),
    });
  }
  for (const length of [0, 36, 37, 54, 55]) {
    const authData = Buffer.alloc(length);
    if (length === 55) authData[54] = 1;
    const failure = parseAttestAuthData(authData);
    assert.ok(failure instanceof IosPhaseFail);
    assert.equal(failure.message, length < 37 ? `authData is ${length} bytes, < 37`
      : length < 55 ? 'authData missing attested credential data' : 'authData truncated within credentialId');
  }
});

test('CBOR preserves supported types and offsets', () => {
  for (const [hex, expected] of [
    ['00', 0], ['17', 23], ['182a', 42], ['190100', 256], ['1a00010000', 65536],
    ['1b0000000100000000', 4294967296], ['29', -10], ['43010203', Buffer.from([1, 2, 3])],
    ['63616263', 'abc'], ['820102', [1, 2]], ['a20102616103', new Map<string | number, number>([[1, 2], ['a', 3]])],
  ] as const) {
    const bytes = Buffer.from(hex, 'hex');
    assert.deepEqual(decodeCbor(Buffer.concat([Buffer.from([0]), bytes, Buffer.from([0])]), 1), [expected, bytes.length + 1]);
  }
});

test('CBOR rejects malformed and unsupported encodings', () => {
  for (const hex of ['', '18', '430102', '636162', '8201', 'a16161', '9f01ff', 'bf616101ff',
    '5f4101ff', '7f6161ff', 'c001', 'f5', 'f6', 'f93c00', 'a18001', '1bffffffffffffffff', 'a26161f5616101']) {
    assert.throws(() => decodeCbor(Buffer.from(hex, 'hex')), hex);
  }
});

test('CBOR enforces nesting limits', () => {
  decodeCbor(Buffer.concat([Buffer.alloc(16, 0x81), Buffer.from([0])]));
  assert.throws(() => decodeCbor(Buffer.concat([Buffer.alloc(17, 0x81), Buffer.from([0])])));
});

test('CBOR library output works with App Attest callers', () => {
  const authData = Buffer.alloc(37, 1);
  const certificate = Buffer.from([1, 2, 3]);
  const token = Buffer.from(encode({
    fmt: 'apple-appattest', authData,
    attStmt: { x5c: [certificate, certificate], receipt: Buffer.from([4]) },
  })).toString('base64');
  assert.deepEqual(parseAppAttestToken(token).credCertDer, certificate);
  assert.deepEqual(parseAppAttestToken(token).authData, authData);
  const assertion = Buffer.from(encode({ signature: certificate, authenticatorData: authData })).toString('base64');
  assert.deepEqual(parseAppAttestAssertion(assertion).signature, certificate);
});

test('App Attest schema rejects malformed maps and required fields', () => {
  const certificate = Buffer.from([1, 2, 3]);
  const valid = { fmt: 'apple-appattest', authData: Buffer.alloc(37), attStmt: { x5c: [certificate, certificate] } };
  for (const input of [
    [], 'text', 1, {},
    { ...valid, fmt: 1 }, { ...valid, authData: 'bytes' },
    { ...valid, attStmt: [] }, { ...valid, attStmt: {} },
    { ...valid, attStmt: { x5c: [certificate] } },
    { ...valid, attStmt: { x5c: ['bytes', certificate] } },
    { ...valid, attStmt: { x5c: [certificate, 'bytes'] } },
  ]) {
    assert.throws(() => parseAppAttestToken(Buffer.from(encode(input)).toString('base64')));
  }
});

test('App Attest schema preserves buffers, optional receipts, and extra-field tolerance', () => {
  const certificate = Buffer.from([0, 255, 128]);
  const authData = Buffer.from([255, 0, 128]);
  for (const receipt of [undefined, 'ignored', Buffer.from([255, 0])]) {
    const attStmt = { x5c: [certificate, certificate, 'ignored'], ...(receipt === undefined ? {} : { receipt }) };
    const input = { fmt: 'policy-checked-later', authData, attStmt, extra: 1 };
    const result = parseAppAttestToken(Buffer.from(encode(input)).toString('base64'));
    assert.deepEqual(result, {
      fmt: input.fmt, authData, credCertDer: certificate, intermediateDer: certificate,
      receipt: Buffer.isBuffer(receipt) ? receipt : null,
      receiptLength: Buffer.isBuffer(receipt) ? receipt.length : 0,
    });
  }
});

test('App assertion schema enforces byte strings and minimum authenticator length', () => {
  const signature = Buffer.from([255, 0, 128]);
  const authenticatorData = Buffer.alloc(37, 255);
  for (const input of [
    [], 'text', {}, { signature }, { authenticatorData },
    { signature: 'bytes', authenticatorData }, { signature, authenticatorData: 'bytes' },
    { signature, authenticatorData: Buffer.alloc(36) },
  ]) {
    assert.throws(() => parseAppAttestAssertion(Buffer.from(encode(input)).toString('base64')));
  }
  for (const length of [37, 38]) {
    const input = { signature, authenticatorData: Buffer.alloc(length, 255) };
    assert.deepEqual(parseAppAttestAssertion(Buffer.from(encode({ ...input, extra: 1 })).toString('base64')), input);
  }
});