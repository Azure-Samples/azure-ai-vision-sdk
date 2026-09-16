import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { test } from 'node:test';
import { computeCertThumbprint } from '../src/cert_utils';
import { setAttestationLogger, type AttestationLogger, type TelemetryProps } from '../src/logging';
import { getSessionData } from '../src/server_utils';
import type { ClusterStore } from '../src/store';

const directConsoleCall = /\bconsole\s*\.\s*(?:debug|error|info|log|trace|warn)\s*\(/g;

async function findTypeScriptFiles(directory: string): Promise<string[]> {
  const entries = await readdir(directory, { withFileTypes: true });
  const nestedFiles = await Promise.all(entries.map(entry => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory()
      ? findTypeScriptFiles(entryPath)
      : Promise.resolve(entry.name.endsWith('.ts') ? [entryPath] : []);
  }));
  return nestedFiles.flat();
}

test('library source emits diagnostics only through AttestationLogger', async () => {
  const sourceDirectory = path.join(process.cwd(), 'src');
  const violations: string[] = [];

  for (const filePath of await findTypeScriptFiles(sourceDirectory)) {
    const source = await readFile(filePath, 'utf8');
    for (const match of source.matchAll(directConsoleCall)) {
      const line = source.slice(0, match.index).split('\n').length;
      violations.push(`${path.relative(process.cwd(), filePath)}:${line}`);
    }
  }

  assert.deepEqual(violations, []);
});

test('diagnostics are sent to the injected AttestationLogger', async () => {
  const events: Array<{ name: string; properties?: TelemetryProps }> = [];
  const exceptions: Array<{ error: unknown; properties?: TelemetryProps }> = [];
  const logger: AttestationLogger = {
    trackEvent: (name, properties) => events.push({ name, properties }),
    trackException: (error, properties) => exceptions.push({ error, properties }),
    trackDependency() {},
  };
  setAttestationLogger(logger);

  try {
    const sessionId = '12345678-1234-1234-1234-123456789abc';
    const store = { getSession: async () => null } as unknown as ClusterStore;
    assert.equal(await getSessionData(store, sessionId), null);
    assert.equal(computeCertThumbprint('not a certificate'), null);

    assert.deepEqual(events, [{
      name: 'SessionStore.GetSessionFail',
      properties: { reason: 'NOT_FOUND', sid: sessionId },
    }]);
    assert.equal(exceptions.length, 1);
    assert.deepEqual(exceptions[0].properties, { source: 'computeCertThumbprint' });
  } finally {
    setAttestationLogger({
      trackEvent() {},
      trackException() {},
      trackDependency() {},
    });
  }
});