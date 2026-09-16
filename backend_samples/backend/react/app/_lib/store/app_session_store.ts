/**
 * App-owned session store.
 *
 * Holds the sample's own per-session context — the Face resource + API key +
 * action it needs to poll the liveness result later. This is kept in a SEPARATE
 * Redis key (`session/<sid>`) from the attestation library's session record
 * (stored under the bare `<sid>`), so the two never share a JSON blob:
 *   - the library owns its record (token + attestation state) entirely, and
 *   - the app owns these polling credentials, which the library never sees.
 *
 * This is what lets the attestation code move into a standalone package without
 * dragging any app-specific fields along. A fresh client is created/connected
 * per operation (then disconnected in a `finally`), matching the cluster store.
 */
import { createRedisClient, trackRedisOp } from './redis_client';
import { trackException } from '../app_insights_server';

const APP_SESSION_KEY_PREFIX = 'session/';

/** Session TTL in seconds (SESSION_TOKEN_TTL, default 10 minutes). Matches the
 * attestation session so the app context and the library record expire together. */
function sessionTtlSeconds(): number {
  return parseInt(process.env.SESSION_TOKEN_TTL || '600', 10);
}

function appSessionKey(sid: string): string {
  return `${APP_SESSION_KEY_PREFIX}${sid}`;
}

/** The app's own per-session context (never seen by the attestation library). */
export interface AppSession {
  /** Azure Face resource name (host pinned to *.cognitiveservices.azure.com). */
  resource: string;
  /** Face API key, used server-side to poll the liveness result. */
  apiKey: string;
  /** Liveness action: 'detectLiveness' | 'detectLivenessWithVerify'. */
  action: string;
}

/** Persist the app session context under `session/<sid>` with a fresh TTL. */
export async function saveAppSession(sid: string, data: AppSession): Promise<boolean> {
  const key = appSessionKey(sid);
  const startTime = Date.now();
  const client = await createRedisClient();
  if (!client) {
    trackRedisOp({
      command: 'SET',
      key,
      duration: Date.now() - startTime,
      success: false,
      resultCode: 'CLIENT_CREATE_FAIL',
      properties: { op: 'saveAppSession' },
    });
    return false;
  }
  try {
    client.on('error', (err: unknown) => console.log('Redis Client Error', err));
    await client.connect();
    const value = JSON.stringify(data);
    const ttl = sessionTtlSeconds();
    await client.set(key, value, { EX: ttl });
    trackRedisOp({
      command: 'SET',
      key,
      duration: Date.now() - startTime,
      success: true,
      properties: { op: 'saveAppSession', ttlSec: ttl, valueBytes: value.length },
    });
    return true;
  } catch (error) {
    trackRedisOp({
      command: 'SET',
      key,
      duration: Date.now() - startTime,
      success: false,
      resultCode: 'EXCEPTION',
      properties: { op: 'saveAppSession' },
    });
    trackException(error, { source: 'saveAppSession', key });
    console.error('Error in saveAppSession:', error);
    return false;
  } finally {
    await client.disconnect();
  }
}

/** Read the app session context stored under `session/<sid>`, or null if absent. */
export async function getAppSession(sid: string): Promise<AppSession | null> {
  const key = appSessionKey(sid);
  const startTime = Date.now();
  const client = await createRedisClient();
  if (!client) {
    trackRedisOp({
      command: 'GET',
      key,
      duration: Date.now() - startTime,
      success: false,
      resultCode: 'CLIENT_CREATE_FAIL',
      properties: { op: 'getAppSession' },
    });
    return null;
  }
  try {
    client.on('error', (err: unknown) => console.log('Redis Client Error', err));
    await client.connect();
    const raw = await client.get(key);
    const duration = Date.now() - startTime;
    if (!raw) {
      trackRedisOp({
        command: 'GET',
        key,
        duration,
        success: true,
        resultCode: 'MISS',
        properties: { op: 'getAppSession' },
      });
      return null;
    }
    trackRedisOp({
      command: 'GET',
      key,
      duration,
      success: true,
      resultCode: 'HIT',
      properties: { op: 'getAppSession', valueBytes: raw.length },
    });
    const parsed = JSON.parse(raw) as Partial<AppSession>;
    return {
      resource: parsed.resource ?? '',
      apiKey: parsed.apiKey ?? '',
      action: parsed.action ?? '',
    };
  } catch (error) {
    trackRedisOp({
      command: 'GET',
      key,
      duration: Date.now() - startTime,
      success: false,
      resultCode: 'EXCEPTION',
      properties: { op: 'getAppSession' },
    });
    trackException(error, { source: 'getAppSession', key });
    console.error('Error in getAppSession:', error);
    return null;
  } finally {
    await client.disconnect();
  }
}
