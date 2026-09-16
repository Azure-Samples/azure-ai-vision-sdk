import assert from 'node:assert/strict';
import { test } from 'node:test';
import { verifyIntegrityTimestamp } from '../src/android/integrity_checks';

test('Play Integrity timestamp accepts a fresh value', () => {
  const result = verifyIntegrityTimestamp({
    requestDetails: { timestampMillis: Date.now().toString() },
  });

  assert.equal(result, null);
});

test('Play Integrity timestamp rejects missing or invalid values', () => {
  for (const verdict of [
    {},
    { requestDetails: {} },
    { requestDetails: { timestampMillis: 'not-a-timestamp' } },
  ]) {
    const result = verifyIntegrityTimestamp(verdict);
    assert.equal(result?.reason, 'INTEGRITY_TIMESTAMP_INVALID');
  }
});