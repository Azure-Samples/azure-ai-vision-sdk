/**
 * Session storage (session-domain layer).
 *
 *   saveToken                — seed a session with its token (empty state; fresh TTL owned by the store)
 *   getSessionData           — read the {token, data, sid} record
 *   updateSessionData        — write back {token, data}, preserving the existing TTL
 *
 * Every storage-touching function receives a `ClusterStore` (see ./store) by
 * dependency injection and delegates ALL persistence — connection, key schema,
 * serialization, TTL, and storage telemetry — to it. This module keeps only the
 * session-domain concerns (UUID validation, domain events). Certificate storage
 * lives in `./cert_store`.
 */
import { trackEvent } from './logging';
import type { ClusterStore } from './store';
import { z } from 'zod';

export const sessionIdSchema = z.guid({ error: issue => !issue.input ? 'MISSING_SESSION_ID' : 'INVALID_SESSION_ID' })
  .length(36, { error: 'INVALID_SESSION_ID' });

export async function saveToken(
  store: ClusterStore,
  sid: string,
  token: string
): Promise<string | null> {
  const stored = await store.setSession(sid, { token, data: {} });
  return stored ? sid : null;
}

export async function getSessionData(
  store: ClusterStore,
  sid: string
): Promise<{ token: string; data: Record<string, unknown>; sid: string; version: string } | null> {
  if (!sessionIdSchema.safeParse(sid).success) {
    trackEvent('SessionStore.GetSessionFail', { reason: 'INVALID_UUID', sid: sid ?? null });
    return null;
  }

  const record = await store.getSession(sid);
  if (!record) {
    trackEvent('SessionStore.GetSessionFail', { reason: 'NOT_FOUND', sid });
    return null;
  }
  return { ...record.value, version: record.version, sid };
}

export async function updateSessionData(
  store: ClusterStore,
  sid: string,
  token: string,
  data: Record<string, unknown>,
  expectedVersion: string,
): Promise<boolean> {
  if (!sessionIdSchema.safeParse(sid).success) {
    trackEvent('SessionStore.UpdateFail', { reason: 'INVALID_UUID', sid: sid ?? null });
    return false;
  }
  return (await store.updateSession(sid, expectedVersion, { token, data })) === 'applied';
}
