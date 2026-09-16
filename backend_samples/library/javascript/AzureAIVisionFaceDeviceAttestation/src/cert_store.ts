/**
 * Certificate store, keyed by SHA-256 thumbprint of the cert DER. Persistence,
 * key namespace (cert_thumb:*) and TTL are delegated to an injected ClusterStore.
 *
 *   saveCertificate           — creates `cert_thumb:{thumbprint}` on first sight,
 *                               bumps lastVerifiedAt on subsequent calls
 *   getCertificate            — load record by thumbprint (callers compare
 *                               clientId / system fields to detect cross-identity
 *                               reuse)
 *   updateCertificateMetadata — merge fields into .metadata, bump lastVerifiedAt,
 *                               preserve TTL (used to advance lastAssertionSignCount)
 *
 * The cert TTL is bounded by env `CERT_TTL` (default 7 days), so stale records
 * naturally drop and clients are forced through re-registration. The metadata
 * blob holds the platform-specific verdict (PlayIntegrityVerdict /
 * AppAttestVerdict) so post-attestation we retain everything we decoded.
 */
import { computeCertThumbprint } from './cert_utils';
import { trackEvent } from './logging';
import type { ClusterStore, Snapshot } from './store';

/**
 * Certificate data structure for app attestation
 */
export interface CertificateData {
  clientId: string;
  system: 'ios' | 'android';
  thumbprint: string; // SHA256 hex (64 chars)
  publicCert: string; // PEM format
  createdAt: string; // ISO 8601
  lastVerifiedAt: string; // ISO 8601
  metadata?: object;
}

/**
 * Save a certificate through the injected store.
 * @param clientId - Client application ID
 * @param system - Platform ('ios' or 'android')
 * @param publicCert - Certificate in PEM format
 * @param metadata - Optional metadata object
 * @returns Object with thumbprint and isNew flag, or null on error
 */
export async function saveCertificate(
  store: ClusterStore,
  clientId: string,
  system: 'ios' | 'android',
  publicCert: string,
  metadata?: object,
): Promise<{ thumbprint: string; isNew: boolean } | null> {
  // Compute thumbprint (domain concern; the store owns the key schema + TTL).
  const thumbprint = computeCertThumbprint(publicCert);
  if (!thumbprint) {
    trackEvent('CertStore.SaveFail', {
      reason: 'THUMBPRINT_COMPUTE_FAIL',
      system,
    });
    return null;
  }

  // Existing cert: keep the record, just bump lastVerifiedAt. New cert: build a
  // fresh record. Updates preserve expiry; creation uses the default TTL.
  const existing = await store.getCertificate(thumbprint);
  if (existing) {
    if (existing.value.clientId !== clientId || existing.value.system !== system) return null;
    existing.value.lastVerifiedAt = new Date().toISOString();
    const result = await store.updateCertificate(thumbprint, existing.version, existing.value);
    if (result !== 'applied') {
      return null;
    }
    trackEvent('CertStore.Updated', {
      thumbprint,
      clientId,
      system,
      isNew: false,
    });
    return { thumbprint, isNew: false };
  }

  const now = new Date().toISOString();
  const certData: CertificateData = {
    clientId,
    system,
    thumbprint,
    publicCert,
    createdAt: now,
    lastVerifiedAt: now,
    metadata,
  };

  const ok = await store.setCertificate(thumbprint, certData);
  if (!ok) {
    return null;
  }
  trackEvent('CertStore.Created', {
    thumbprint,
    clientId,
    system,
    isNew: true,
  });
  return { thumbprint, isNew: true };
}

/**
 * Get a certificate snapshot from the injected store by thumbprint.
 * @param thumbprint - Certificate thumbprint (SHA-256 hex)
 * @returns Certificate data or null if not found
 */
export async function getCertificate(
  store: ClusterStore,
  thumbprint: string,
): Promise<Snapshot<CertificateData> | null> {
  return store.getCertificate(thumbprint);
}

/**
 * Merge fields into a cert record's `metadata` and bump `lastVerifiedAt`,
 * preserving the existing certificate expiry. Used by the
 * ongoing-assertion path to advance `lastAssertionSignCount` after each
 * successful per-call verification. Returns false on a stale version or a
 * missing/expired record. Storage failures throw; callers must fail closed.
 */
export async function updateCertificateMetadata(
  store: ClusterStore,
  thumbprint: string,
  partialMetadata: Record<string, unknown>,
  snapshot: Snapshot<CertificateData>,
): Promise<boolean> {
  const existing = snapshot.value;

  existing.lastVerifiedAt = new Date().toISOString();
  existing.metadata = {
    ...(existing.metadata as Record<string, unknown> | undefined),
    ...partialMetadata,
  };

  return (await store.updateCertificate(thumbprint, snapshot.version, existing)) === 'applied';
}

