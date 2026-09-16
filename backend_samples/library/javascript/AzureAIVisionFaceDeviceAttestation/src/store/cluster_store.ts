/**
 * Cluster-wide persistent-storage abstraction for the attestation flow.
 *
 * Two entry kinds are stored: liveness **sessions** (keyed by session UUID) and
 * attestation **certificates** (keyed by SHA-256 thumbprint). Each is exposed as
 * get / set / update.
 *
 * The store owns ALL time-to-live policy:
 *   - `set*`    creates an absent entry with the store's configured default
 *               TTL (session vs certificate TTL).
 *   - `update*` rewrites an existing entry while PRESERVING its remaining TTL,
 *               conditional on the exact snapshot version.
 * Reads return detached snapshots. Every successful write gets a unique version,
 * including recreation after expiry. Storage failures throw StorageError; they
 * must never be reported as a missing record or a successful write.
 * Callers never read a TTL env var, compute a TTL, or pass one in.
 *
 * The concrete implementation and `getAttestationStore()` factory are provided
 * app-side (see app/_lib/store), NOT by this library — so a consumer can back the
 * store with Redis, SQL, or anything else. The implementation owns connection,
 * key namespace (e.g. the `cert_thumb:` prefix), TTL, and storage telemetry.
 *
 * Route handlers and the session/certificate helpers receive a `ClusterStore`
 * by explicit dependency injection, so the attestation code never talks to a
 * storage backend directly — only to this interface.
 */
import type { CertificateData } from '../cert_store';

/**
 * Value stored under a session UUID: the Face session token plus the session
 * state object. The store owns how this record is serialized on the wire —
 * callers only ever see this structured shape, never a storage format.
 */
export interface SessionRecord {
  token: string;
  data: Record<string, unknown>;
}

export interface Snapshot<T> {
  value: T;
  version: string;
}

export type UpdateResult = 'applied' | 'conflict' | 'missingOrExpired';

export class StorageError extends Error {}

export interface ClusterStore {
  /** Read a session record by UUID, or null if absent/expired. */
  getSession(sid: string): Promise<Snapshot<SessionRecord> | null>;
  /** Create only if absent with a fresh TTL; false means already exists. */
  setSession(sid: string, record: SessionRecord): Promise<boolean>;
  /**
   * Rewrite an existing session, preserving its remaining TTL.
  * Reject stale versions and absent/expired records, without changing expiry.
   */
  updateSession(sid: string, expectedVersion: string, record: SessionRecord): Promise<UpdateResult>;

  /** Read a certificate record by thumbprint, or null if absent/expired. */
  getCertificate(thumbprint: string): Promise<Snapshot<CertificateData> | null>;
  /** Create only if absent with a fresh TTL; false means already exists. */
  setCertificate(thumbprint: string, data: CertificateData): Promise<boolean>;
  /**
   * Rewrite an existing certificate, preserving its remaining TTL.
  * Reject stale versions and absent/expired records, without changing expiry.
   */
  updateCertificate(thumbprint: string, expectedVersion: string, data: CertificateData): Promise<UpdateResult>;
}

export type { CertificateData };
