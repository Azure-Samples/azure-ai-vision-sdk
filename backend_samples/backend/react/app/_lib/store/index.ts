/**
 * App-side attestation-store wiring.
 *
 * Selects and constructs the concrete {@link ClusterStore} implementation the
 * attestation library uses for this deployment (currently Redis). The library
 * only depends on the `ClusterStore` interface (exported from
 * `@azure/ai-vision-face-deviceattestation`); swap the backend here (e.g. a
 * SQL-backed store on another server) without touching it.
 */
export { getAttestationStore, RedisAttestationStore } from './redis_attestation_store';
export { saveAppSession, getAppSession, type AppSession } from './app_session_store';
