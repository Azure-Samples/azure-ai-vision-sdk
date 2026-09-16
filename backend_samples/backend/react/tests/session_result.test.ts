import assert from 'node:assert/strict';
import { test } from 'node:test';
import { NextRequest } from 'next/server';
import { GET } from '../app/api/session/result/route';
import { getAttestationService } from '../app/_lib/attestation_service';
import * as appSessions from '../app/_lib/store/app_session_store';

const digestCases: [string, string | undefined, unknown, boolean, boolean, number, string][] = [
  ['matching', 'client-digest', 'client-digest', true, true, 200, 'done'],
  ['mismatched', 'client-digest', 'other-digest', true, true, 409, 'error'],
  ['case changed', 'client-digest', 'CLIENT-DIGEST', true, true, 409, 'error'],
  ['missing service', 'client-digest', undefined, true, true, 409, 'error'],
  ['null service', 'client-digest', null, true, true, 409, 'error'],
  ['missing client', undefined, 'service-digest', true, true, 409, 'error'],
  ['both missing', undefined, undefined, true, true, 409, 'error'],
  ['empty', '', '', true, true, 409, 'error'],
  ['blank', ' ', ' ', true, true, 409, 'error'],
  ['numeric service', '123', 123, true, true, 409, 'error'],
  ['client pending', 'client-digest', 'other-digest', false, true, 200, 'pending'],
  ['service pending', 'client-digest', undefined, true, false, 200, 'pending'],
];

for (const [name, clientDigest, serviceDigest, completed, hasDecision, expectedStatus, expectedState] of digestCases) {
  test(`result digest validation: ${name}`, async context => {
    context.mock.method(appSessions, 'getAppSession', async () => ({
      resource: 'test-resource', apiKey: 'test-key', action: 'detectLiveness',
    }));
    context.mock.method(getAttestationService(), 'getLivenessOutcome', async () => ({ completed, clientDigest }));
    const upstream = { results: { attempts: [{ result: {
      ...(hasDecision ? { livenessDecision: 'real' } : {}),
      ...(serviceDigest === undefined ? {} : { digest: serviceDigest }),
    } }] } };
    const fetchMock = context.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(upstream), {
      headers: { 'Content-Type': 'application/json' },
    }));

    const response = await GET(new NextRequest('http://localhost/api/session/result?s=11111111-1111-1111-1111-111111111111'));
    assert.equal(response.status, expectedStatus);
    const body = await response.json();
    assert.equal(body.status, expectedState);
    assert.equal(fetchMock.mock.callCount(), completed ? 1 : 0);
    if (expectedState === 'done') {
      assert.equal(body.clientDigest, clientDigest);
      assert.deepEqual(body.result, upstream);
    } else {
      assert.equal('result' in body, false);
      assert.equal('clientDigest' in body, false);
      if (expectedState === 'error') assert.equal(body.code, 'DIGEST_MISMATCH');
    }
  });
}