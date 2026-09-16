/**
 * The cluster persistent-storage CONTRACT exposed by the attestation library:
 * the `ClusterStore` interface and its record types. The concrete implementation
 * and `getAttestationStore()` factory are provided app-side (see app/_lib/store), so
 * a consumer can back the store with Redis, SQL, etc.
 */
export type { ClusterStore, SessionRecord, CertificateData, Snapshot, UpdateResult } from './cluster_store';
export { StorageError } from './cluster_store';
