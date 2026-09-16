import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseAndroidAttestJson, verifyAndroidChainSignaturesAndRoot } from '../src/android/chain_checks';
import { AndroidPhaseFail } from '../src/android/types';
import { pemToDer } from '../src/cert_utils';
import { GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS } from '../src/android/google_roots';
import { APPLE_APP_ATTEST_ROOT_CAS } from '../src/ios/constants';
import { verifyIosX5cChain } from '../src/ios/attestation_phases';

test('path phases distinguish untrusted Android roots from invalid pinned paths', async () => {
  const appleRoot = pemToDer(APPLE_APP_ATTEST_ROOT_CAS[0]);
  const googleRoot = pemToDer(GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS[0]);
  const untrusted = await verifyAndroidChainSignaturesAndRoot([appleRoot]);
  assert.ok(untrusted instanceof AndroidPhaseFail);
  assert.equal(untrusted.reason, 'ROOT_CA_MISMATCH');
  const incomplete = await verifyAndroidChainSignaturesAndRoot([googleRoot]);
  assert.ok(incomplete instanceof AndroidPhaseFail);
  assert.equal(incomplete.reason, 'CHAIN_PATH_INVALID');
  const appleFailure = await verifyIosX5cChain(googleRoot, appleRoot);
  assert.equal(appleFailure?.reason, 'CHAIN_PATH_INVALID');
});

test('Android schema preserves token, certificate strings, and additional fields', () => {
  const input = { token: ' token\n', certificateChain: [' YWJj\n', 'ZA=='], extra: 'kept' };
  assert.deepEqual(parseAndroidAttestJson(JSON.stringify(input), {}), input);
});

test('Android schema preserves missing-token handling for the verification phase', () => {
  for (const input of [
    { certificateChain: ['YQ=='] },
    { certificateChain: ['YQ=='], token: null },
    { certificateChain: ['YQ=='], token: '' },
  ]) {
    assert.deepEqual(parseAndroidAttestJson(JSON.stringify(input), {}), input);
  }
});

test('Android schema rejects malformed envelopes with phase error codes', () => {
  const cases: [string, string][] = [
    ['{', 'ATTEST_JSON_PARSE_ERROR'],
    ...[null, [], 1, 'text', {}, { certificateChain: 'YQ==' },
      { certificateChain: [1] }, { certificateChain: [null] }, { certificateChain: [{}] },
    ].map((input): [string, string] => [JSON.stringify(input), 'INVALID_CHAIN_FORMAT']),
    [JSON.stringify({ certificateChain: [] }), 'EMPTY_CHAIN'],
    ...[1, true, {}, []].map((token): [string, string] => [
      JSON.stringify({ certificateChain: ['YQ=='], token }), 'MISSING_INTEGRITY_TOKEN',
    ]),
  ];
  for (const [json, reason] of cases) {
    const result = parseAndroidAttestJson(json, {});
    assert.ok(result instanceof AndroidPhaseFail, json);
    assert.equal(result.reason, reason, json);
  }
});