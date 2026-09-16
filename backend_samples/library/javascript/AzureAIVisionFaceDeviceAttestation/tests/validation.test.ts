import assert from 'node:assert/strict';
import { randomUUID, sign } from 'node:crypto';
import { test } from 'node:test';
import { encryptedBodySchema, registrationPayloadSchema, sessionIdSchema, signedBodySchema,
  validateIdentity, validateInput, validatePayload, verificationPayloadSchema } from '../src/handlers/validation';
import { handleAttestationChallenge } from '../src/handlers/challenge';
import { handleAttestationRegister } from '../src/handlers/register';
import { handleAttestationVerify } from '../src/handlers/verify';
import { handleSessionToken } from '../src/handlers/token';
import { handleLivenessDigest } from '../src/handlers/digest';
import { checkCertificateRevocation, fetchRevocationStatusList } from '../src/android/revocation';
import { StorageError, type ClusterStore, type Snapshot, type SessionRecord, type CertificateData, type UpdateResult } from '../src/store';
import { AttestationService } from '../src/service';
import { generateServerKeyPairEC, encryptWithPublicKeyEC, decryptWithPrivateKeyEC } from '../src/crypto_utils';
import { updateCertificateMetadata } from '../src/cert_store';
import { getSessionData, updateSessionData } from '../src/server_utils';

const identity = { sessionId: '12345678-1234-1234-1234-123456789abc', clientId: ' client ', system: 'iOS' };

class CasStore implements ClusterStore {
  entries = new Map<string, Snapshot<SessionRecord | CertificateData>>();
  rejectWrites = false;
  throwWrites = false;
  private async read<T>(key: string): Promise<Snapshot<T> | null> {
    return structuredClone(this.entries.get(key) as Snapshot<T> | undefined) ?? null;
  }
  private async create(key: string, value: SessionRecord | CertificateData): Promise<boolean> {
    if (this.entries.has(key)) return false;
    this.entries.set(key, structuredClone({ value, version: randomUUID() }));
    return true;
  }
  private async update(key: string, version: string, value: SessionRecord | CertificateData): Promise<UpdateResult> {
    if (this.throwWrites) throw new StorageError('Injected storage failure');
    const current = this.entries.get(key);
    if (!current) return 'missingOrExpired';
    if (this.rejectWrites || current.version !== version) return 'conflict';
    this.entries.set(key, structuredClone({ value, version: randomUUID() }));
    return 'applied';
  }
  getSession(key: string) { return this.read<SessionRecord>(key); }
  setSession(key: string, value: SessionRecord) { return this.create(key, value); }
  updateSession(key: string, version: string, value: SessionRecord) { return this.update(key, version, value); }
  getCertificate(key: string) { return this.read<CertificateData>(key); }
  setCertificate(key: string, value: CertificateData) { return this.create(key, value); }
  updateCertificate(key: string, version: string, value: CertificateData) { return this.update(key, version, value); }
}

test('CAS rejects stale snapshots, duplicate creation, missing records and ABA recreation', async () => {
  const store = new CasStore();
  const value = { token: 'token', data: {} };
  assert.equal(await store.setSession(identity.sessionId, value), true);
  assert.equal(await store.setSession(identity.sessionId, value), false);
  const snapshot = (await store.getSession(identity.sessionId))!;
  snapshot.value.data.modified = true;
  assert.deepEqual((await store.getSession(identity.sessionId))!.value.data, {});
  assert.equal(await store.updateSession(identity.sessionId, snapshot.version, snapshot.value), 'applied');
  assert.equal(await store.updateSession(identity.sessionId, snapshot.version, value), 'conflict');
  store.entries.delete(identity.sessionId);
  assert.equal(await store.updateSession(identity.sessionId, snapshot.version, value), 'missingOrExpired');
  await store.setSession(identity.sessionId, value);
  assert.equal(await store.updateSession(identity.sessionId, snapshot.version, value), 'conflict');
});

test('certificate metadata commits use the verified revision and cannot regress counters', async () => {
  const store = new CasStore();
  await store.setCertificate('cert', { clientId: 'client', system: 'ios', thumbprint: 'cert',
    publicCert: 'pem', createdAt: '', lastVerifiedAt: '', metadata: { lastAssertionSignCount: 0 } });
  const lower = (await store.getCertificate('cert'))!;
  const higher = (await store.getCertificate('cert'))!;
  assert.equal(await updateCertificateMetadata(store, 'cert', { lastAssertionSignCount: 2 }, higher), true);
  assert.equal(await updateCertificateMetadata(store, 'cert', { lastAssertionSignCount: 1 }, lower), false);
  assert.deepEqual((await store.getCertificate('cert'))!.value.metadata, { lastAssertionSignCount: 2 });
});

test('signed concurrent token requests have one winner and rejected writes release no token', async context => {
  context.mock.method(console, 'log', () => {});
  const keys = generateServerKeyPairEC()!;
  const data = { serverKeyGenerated: true, certRegistered: true, challengeHash: 'challenge',
    clientId: 'client', system: 'android', clientAuthPublicKey: keys.publicKey,
    clientEncryptionPublicKey: keys.publicKey, serverEncryptionPrivateKey: keys.privateKey };
  const encryptedData = encryptWithPublicKeyEC(JSON.stringify(data), keys.publicKey)!;
  const signature = sign('sha256', Buffer.from(encryptedData), keys.privateKey).toString('base64');
  const req = { sessionId: identity.sessionId, body: { encryptedData, signature } };
  const store = new CasStore();
  const service = new AttestationService(store);
  await store.setSession(identity.sessionId, { token: 'secret-token', data });
  const responses = await Promise.all([service.sessionToken(req), service.sessionToken(req)]);
  assert.deepEqual(responses.map(response => response.status).sort(), [200, 409]);
  const winner = responses.find(response => response.ok)!;
  assert.ok('encryptedData' in winner.body);
  assert.equal(JSON.parse(decryptWithPrivateKeyEC(winner.body.encryptedData, keys.privateKey)!).token, 'secret-token');
  assert.equal((await service.sessionToken(req)).status, 409);
  for (const throws of [false, true]) {
    store.entries.clear();
    await store.setSession(identity.sessionId, { token: 'secret-token', data });
    store.rejectWrites = !throws;
    store.throwWrites = throws;
    const result = await service.sessionToken(req);
    assert.equal(result.status, throws ? 503 : 409);
    assert.ok(!('encryptedData' in result.body));
    assert.equal((await store.getSession(identity.sessionId))!.value.data.authCompleted, undefined);
  }
});

test('session ID schemas reject malformed IDs before handler or storage access', async context => {
  context.mock.method(console, 'error', () => {});
  const store = new Proxy({} as ClusterStore, { get: () => { throw new Error('Unexpected storage access'); } });
  for (const sessionId of ['', 'bad', identity.sessionId.replaceAll('-', ''), `{${identity.sessionId}}`,
    `(${identity.sessionId})`, ` ${identity.sessionId}`, `${identity.sessionId} `, `${identity.sessionId}\n`,
    identity.sessionId.replace('abc', 'abg'), identity.sessionId.replace('-', '_')]) {
    const expected = sessionId ? 'INVALID_SESSION_ID' : 'MISSING_SESSION_ID';
    assert.equal(sessionIdSchema.safeParse(sessionId).success, false);
    assert.equal(await getSessionData(store, sessionId), null);
    assert.equal(await updateSessionData(store, sessionId, 'token', {}, 'revision'), false);
    assert.equal((await handleAttestationChallenge({ ...identity, sessionId }, store)).code, expected);
    for (const handler of [handleAttestationRegister, handleAttestationVerify, handleSessionToken, handleLivenessDigest]) {
      assert.equal((await handler({ ...identity, sessionId, body: null }, store)).code, expected);
    }
  }
});

test('session ID schemas preserve GUID-like IDs and original storage keys', async () => {
  for (const sid of [identity.sessionId, identity.sessionId.toUpperCase(),
    '00000000-0000-0000-0000-000000000000', 'ffffffff-ffff-ffff-ffff-ffffffffffff']) {
    const record = { token: 'token', data: { marker: true } };
    let reads = 0;
    let writes = 0;
    const store = {
      async getSession(key: string) { assert.equal(key, sid); reads++; return { value: record, version: 'revision' }; },
      async updateSession(key: string, version: string, value: unknown) {
        assert.equal(version, 'revision');
        assert.equal(key, sid); assert.deepEqual(value, record); writes++; return 'applied';
      },
    } as ClusterStore;
    assert.equal(sessionIdSchema.parse(sid), sid);
    assert.deepEqual(await getSessionData(store, sid), { ...record, sid, version: 'revision' });
    assert.equal(await updateSessionData(store, sid, record.token, record.data, 'revision'), true);
    assert.equal(reads, 1);
    assert.equal(writes, 1);
  }
});

test('revocation responses are validated before caching and refresh errors fail closed', async context => {
  context.mock.method(console, 'log', () => {});
  context.mock.method(console, 'error', () => {});
  let now = Date.now() - 180000;
  context.mock.method(Date, 'now', () => now);
  let responseBody: unknown;
  let fetchCount = 0;
  let maxAge = 60;
  context.mock.method(globalThis, 'fetch', async () => {
    fetchCount++;
    return new Response(JSON.stringify(responseBody), { headers: { 'cache-control': `max-age=${maxAge}` } });
  });
  for (const malformed of [null, [], {}, { entries: [] }, { entries: { ab: null } },
    { entries: { ab: { status: 'GOOD' } } }, { entries: { ab: { status: 'REVOKED', reason: 1 } } }]) {
    responseBody = malformed;
    const result = await fetchRevocationStatusList();
    assert.equal(result, null);
    assert.equal(checkCertificateRevocation(Buffer.alloc(0), result).reason, 'REVOCATION_CHECK_UNAVAILABLE');
  }
  responseBody = { entries: {
    ab: { status: 'REVOKED', reason: 'KEY_COMPROMISE', comment: 'test', extra: true },
    cd: { status: 'SUSPENDED' },
  }, extra: true };
  assert.deepEqual(await fetchRevocationStatusList(), responseBody);
  const countAfterFetch = fetchCount;
  assert.deepEqual(await fetchRevocationStatusList(), responseBody);
  assert.equal(fetchCount, countAfterFetch);
  now += 60001;
  responseBody = { entries: null };
  assert.equal(await fetchRevocationStatusList(), null);
  assert.equal(fetchCount, countAfterFetch + 1);
  now += 60001;
  maxAge = 0;
  responseBody = { entries: {} };
  assert.deepEqual(await fetchRevocationStatusList(), responseBody);
});

test('signed-body schemas preserve original strings and distinguish missing fields from invalid types', () => {
  const payload = ' { "challengeHash": "abc", "encryptionPublicCert": "cert", "attestJson": "{}" }\n';
  const body = { payload, authPublicCert: 'cert', signature: 'sig', extra: true };
  assert.deepEqual(signedBodySchema.parse(body), body);
  for (const [input, code] of [
    [null, 'INVALID_JSON_BODY'], [[], 'INVALID_JSON_BODY'], [{}, 'MISSING_BODY_FIELDS'],
    [{ ...body, payload: 123, signature: '' }, 'MISSING_BODY_FIELDS'],
    [{ ...body, payload: 123 }, 'INVALID_PAYLOAD_FORMAT'],
    [{ ...body, payload: ' ' }, 'INVALID_PAYLOAD_FORMAT'],
    [{ ...body, authPublicCert: {} }, 'INVALID_CERT_PEM'],
  ] as const) {
    const result = validateInput(signedBodySchema, input, 'test');
    assert.equal(result.success, false);
    if (!result.success) assert.equal(result.response.code, code);
  }
  assert.equal(validatePayload(registrationPayloadSchema, payload, 'test', identity.sessionId).success, true);
  for (const [input, code] of [['{', 'PAYLOAD_JSON_PARSE_ERROR'], ['{}', 'MISSING_PAYLOAD_FIELDS'],
    ['null', 'MISSING_PAYLOAD_FIELDS'], ['[]', 'MISSING_PAYLOAD_FIELDS'],
    ['{"challengeHash":true,"encryptionPublicCert":"cert"}', 'MISSING_PAYLOAD_FIELDS']] as const) {
    const result = validatePayload(verificationPayloadSchema, input, 'test', identity.sessionId);
    assert.equal(result.success, false);
    if (!result.success) assert.equal(result.response.code, code);
  }
  assert.deepEqual(encryptedBodySchema.parse({ encryptedData: '  abc+/==', signature: 'sig' }),
    { encryptedData: '  abc+/==', signature: 'sig' });
});

test('attestation handlers reject malformed input before accessing storage', async () => {
  const store = new Proxy({} as ClusterStore, { get: () => { throw new Error('Unexpected storage access'); } });
  for (const handler of [handleAttestationRegister, handleAttestationVerify]) {
    assert.equal((await handler({ ...identity, body: null }, store)).code, 'INVALID_JSON_BODY');
    assert.equal((await handler({ ...identity, body: {} }, store)).code, 'MISSING_BODY_FIELDS');
    assert.equal((await handler({ ...identity, body: { payload: '{', authPublicCert: 'cert', signature: 'sig' } }, store)).code,
      'PAYLOAD_JSON_PARSE_ERROR');
  }
  assert.equal((await handleAttestationChallenge({ ...identity, clientId: '' }, store)).code, 'MISSING_CLIENT_ID');
  for (const handler of [handleSessionToken, handleLivenessDigest]) {
    assert.equal((await handler({ sessionId: null, body: null }, store)).code, 'MISSING_SESSION_ID');
    assert.equal((await handler({ sessionId: 'bad', body: null }, store)).code, 'INVALID_SESSION_ID');
    assert.equal((await handler({ ...identity, body: null }, store)).code, 'INVALID_JSON_BODY');
    assert.equal((await handler({ ...identity, body: {} }, store)).code, 'MISSING_ENCRYPTED_DATA');
    assert.equal((await handler({ ...identity, body: { encryptedData: 'abc' } }, store)).code, 'MISSING_SIGNATURE');
  }
});

test('identity schemas preserve GUID-like IDs, client bytes and platform normalization', () => {
  const result = validateIdentity(identity, 'test');
  assert.equal(result.success, true);
  if (result.success) assert.deepEqual(result.data, { ...identity, system: 'ios' });
  assert.equal(sessionIdSchema.safeParse(identity.sessionId.toUpperCase()).success, true);
  assert.equal(sessionIdSchema.safeParse(` ${identity.sessionId}`).success, false);
});

test('identity schemas preserve validation order and reject invalid runtime types', () => {
  for (const [field, value, expected] of [
    ['sessionId', null, 'MISSING_SESSION_ID'], ['sessionId', 'bad', 'INVALID_SESSION_ID'],
    ['sessionId', 123, 'INVALID_SESSION_ID'], ['clientId', '', 'MISSING_CLIENT_ID'],
    ['clientId', '  ', 'INVALID_CLIENT_ID'], ['clientId', {}, 'INVALID_CLIENT_ID'],
    ['system', '', 'MISSING_SYSTEM'], ['system', 'windows', 'INVALID_SYSTEM'],
    ['system', {}, 'INVALID_SYSTEM'],
  ] as const) {
    const result = validateIdentity({ ...identity, [field]: value }, 'test');
    assert.equal(result.success, false);
    if (!result.success) assert.equal(result.response.code, expected);
  }
  const result = validateIdentity({ sessionId: null, clientId: null, system: null }, 'test');
  assert.equal(result.success, false);
  if (!result.success) assert.equal(result.response.code, 'MISSING_SESSION_ID');
});