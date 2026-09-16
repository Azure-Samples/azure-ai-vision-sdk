/**
 * Google Android Key Attestation certificate revocation list:
 * https://android.googleapis.com/attestation/status
 *
 * The list is fetched lazily, cached in-process (Cache-Control max-age
 * honored, default 1h), and consulted for each leaf + chain cert during
 * verification. `checkCertificateRevocation` fails closed: if the list could
 * not be fetched, every cert is treated as revoked so we never accept an
 * unverifiable attestation.
 */
import crypto from 'crypto';
import { z } from 'zod';
import {
  trackEvent,
  trackException,
  trackDependency,
} from '../logging';

/**
 * Certificate revocation status entry
 */
const revocationStatusEntrySchema = z.looseObject({
  status: z.enum(['REVOKED', 'SUSPENDED']),
  expires: z.string().optional(),
  reason: z.enum(['UNSPECIFIED', 'KEY_COMPROMISE', 'CA_COMPROMISE', 'SUPERSEDED', 'SOFTWARE_FLAW']).optional(),
  comment: z.string().optional(),
});
const revocationStatusListSchema = z.looseObject({
  entries: z.record(z.string(), revocationStatusEntrySchema),
});

/**
 * Certificate revocation status list structure
 */
export type RevocationStatusList = z.infer<typeof revocationStatusListSchema>;

/**
 * Cache for revocation status list
 */
const revocationStatusCache: {
  data: RevocationStatusList | null;
  fetchedAt: number;
  ttl: number; // milliseconds
} = {
  data: null,
  fetchedAt: 0,
  ttl: 3600000, // Default 1 hour cache
};

/**
 * Fetch the Google Android attestation revocation status list
 * @returns Revocation status list or null on error
 */
export async function fetchRevocationStatusList(): Promise<RevocationStatusList | null> {
  const REVOCATION_STATUS_URL = 'https://android.googleapis.com/attestation/status';

  // Check cache
  const now = Date.now();
  if (revocationStatusCache.data && (now - revocationStatusCache.fetchedAt) < revocationStatusCache.ttl) {
    trackEvent('AndroidAuth.RevocationList.CacheHit', {
      ageMs: now - revocationStatusCache.fetchedAt,
      ttlMs: revocationStatusCache.ttl,
      entryCount: Object.keys(revocationStatusCache.data.entries || {}).length,
    });
    return revocationStatusCache.data;
  }

  const depStart = Date.now();
  try {
    const response = await fetch(REVOCATION_STATUS_URL);
    const depDuration = Date.now() - depStart;

    if (!response.ok) {
      trackDependency({
        name: 'AndroidAttestation.RevocationList',
        target: 'android.googleapis.com',
        data: REVOCATION_STATUS_URL,
        duration: depDuration,
        success: false,
        resultCode: response.status,
        properties: { statusText: response.statusText },
      });
      trackEvent('AndroidAuth.RevocationList.FetchFailed', {
        status: response.status,
        statusText: response.statusText,
      });
      return null;
    }

    const statusList = revocationStatusListSchema.parse(await response.json());

    // Parse Cache-Control header to determine TTL
    const cacheControl = response.headers.get('cache-control');
    if (cacheControl) {
      const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
      if (maxAgeMatch) {
        revocationStatusCache.ttl = parseInt(maxAgeMatch[1], 10) * 1000; // Convert to milliseconds
      }
    }

    // Update cache
    revocationStatusCache.data = statusList;
    revocationStatusCache.fetchedAt = now;

    const entryCount = Object.keys(statusList.entries || {}).length;
    trackDependency({
      name: 'AndroidAttestation.RevocationList',
      target: 'android.googleapis.com',
      data: REVOCATION_STATUS_URL,
      duration: depDuration,
      success: true,
      resultCode: 200,
      properties: { entryCount, ttlMs: revocationStatusCache.ttl },
    });
    return statusList;
  } catch (error) {
    const depDuration = Date.now() - depStart;
    trackDependency({
      name: 'AndroidAttestation.RevocationList',
      target: 'android.googleapis.com',
      data: REVOCATION_STATUS_URL,
      duration: depDuration,
      success: false,
      resultCode: 'exception',
    });
    trackException(error, { source: 'fetchRevocationStatusList' });
    return null;
  }
}

/**
 * Check if a certificate has been revoked.
 *
 * Fails closed when the status list is unavailable — an attestation we can't
 * check against revocations is rejected, not let through.
 *
 * @param certDer - Certificate in DER format
 * @param statusList - Revocation status list (or null if fetch failed)
 * @returns Object with isRevoked flag and reason
 */
export function checkCertificateRevocation(
  certDer: Buffer,
  statusList: RevocationStatusList | null,
): { isRevoked: boolean; reason?: string; status?: string } {
  if (!statusList || !statusList.entries) {
    // Fail closed: if we can't fetch the revocation list, treat it as a security failure
    trackEvent('AndroidAuth.RevocationList.Unavailable');
    return { isRevoked: true, reason: 'REVOCATION_CHECK_UNAVAILABLE', status: 'REVOKED' };
  }

  try {
    // Parse certificate and extract serial number
    const cert = new crypto.X509Certificate(certDer);
    const serialHex = BigInt(`0x${cert.serialNumber.replace(/:/g, '')}`).toString(16);

    // Check if serial number is in revocation list
    const entry = statusList.entries[serialHex];
    if (entry) {
      trackEvent('AndroidAuth.CertificateRevoked', {
        serialNumber: serialHex,
        status: entry.status,
        reason: entry.reason || 'UNSPECIFIED',
      });
      return {
        isRevoked: true,
        status: entry.status,
        reason: entry.reason || 'UNSPECIFIED',
      };
    }

    return { isRevoked: false };
  } catch (error) {
    trackException(error, { source: 'checkCertificateRevocation' });
    return { isRevoked: false };
  }
}
