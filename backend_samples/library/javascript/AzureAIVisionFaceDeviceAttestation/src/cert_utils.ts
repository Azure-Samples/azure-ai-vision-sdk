/**
 * Certificate utility functions for validation, verification, and chain management
 */

import crypto from 'crypto';
import { BasicConstraints, Certificate, CertificateChainValidationEngine, CryptoEngine } from 'pkijs';
import { trackEvent, trackException } from './logging';

const pathCrypto = new CryptoEngine({
  name: 'node', crypto: crypto.webcrypto as unknown as ConstructorParameters<typeof CryptoEngine>[0]['crypto'],
});
const supportedCriticalExtensions = new Set([
  '2.5.29.15', '2.5.29.17', '2.5.29.19', '2.5.29.30',
  '2.5.29.32', '2.5.29.33', '2.5.29.36', '2.5.29.37', '2.5.29.54',
]);

export async function validateCertificatePath(chainDer: Buffer[], pinnedCAs: string[]): Promise<boolean> {
  try {
    if (chainDer.length < 2 || !matchesPinnedCA(chainDer[chainDer.length - 1], pinnedCAs)) return false;
    const certificates = chainDer.map(der => Certificate.fromBER(new Uint8Array(der)));
    for (const [index, certificate] of certificates.entries()) {
      if (certificate.extensions?.some(extension => extension.critical &&
        (!supportedCriticalExtensions.has(extension.extnID) || !extension.parsedValue ||
          'parsingError' in extension.parsedValue))) return false;
      const constraints = certificate.extensions?.find(extension => extension.extnID === '2.5.29.19')?.parsedValue;
      if (index > 0 && constraints instanceof BasicConstraints && constraints.pathLenConstraint !== undefined) {
        const limit = constraints.pathLenConstraint;
        const subordinateCAs = certificates.slice(1, index).filter(cert => !cert.subject.isEqual(cert.issuer)).length;
        if (typeof limit !== 'number' || limit < 0 || subordinateCAs > limit) return false;
      }
    }
    const engine = new CertificateChainValidationEngine({
      trustedCerts: [certificates[certificates.length - 1]],
      certs: certificates.slice(0, -1).reverse(),
      crls: [], ocsps: [], checkDate: new Date(),
    });
    const result = await engine.verify({ passedWhenNotRevValues: true }, pathCrypto);
    return result.result && result.certificatePath?.length === chainDer.length &&
      result.certificatePath.every((certificate, index) =>
        Buffer.from(certificate.toSchema().toBER(false)).equals(chainDer[index]));
  } catch {
    return false;
  }
}

export function getExtensionValue(certDer: Buffer, oid: string): Buffer | null {
  try {
    const certificate = Certificate.fromBER(new Uint8Array(certDer));
    const matches = certificate.extensions?.filter(extension => extension.extnID === oid) ?? [];
    return matches.length === 1 ? Buffer.from(matches[0].extnValue.valueBlock.valueHexView) : null;
  } catch {
    return null;
  }
}

/**
 * Convert PEM certificate to DER format
 * @param pemCert - Certificate in PEM format
 * @returns Certificate in DER format (Buffer)
 */
export function pemToDer(pemCert: string): Buffer {
  if (!pemCert.trimStart().startsWith('-----BEGIN CERTIFICATE-----') || pemCert.split('-----BEGIN ').length !== 2) {
    throw new Error('Expected a single CERTIFICATE PEM block');
  }
  return new crypto.X509Certificate(pemCert).raw;
}

/**
 * Compute SHA256 thumbprint of a PEM certificate
 * @param pemCert - Certificate in PEM format
 * @returns Hex string (64 chars lowercase) or null if invalid
 */
export function computeCertThumbprint(pemCert: string): string | null {
  try {
    return crypto.createHash('sha256').update(pemToDer(pemCert)).digest('hex');
  } catch (error) {
    trackException(error, { source: 'computeCertThumbprint' });
    return null;
  }
}

/**
 * Validate a PEM certificate
 * @param pemCert - Certificate in PEM format
 * @returns Validation result with certificate details or null if invalid
 */
export function validateCertificate(
  pemCert: string
): { valid: boolean; subject?: string; issuer?: string; validFrom?: Date; validTo?: Date } | null {
  try {
    // Use Node.js crypto.X509Certificate class
    const cert = new crypto.X509Certificate(pemCert);

    // Check expiration
    const now = new Date();
    const validFrom = new Date(cert.validFrom);
    const validTo = new Date(cert.validTo);

    if (now < validFrom) {
      return { valid: false };
    }

    if (now > validTo) {
      return { valid: false };
    }

    // Return parsed details
    return {
      valid: true,
      subject: cert.subject,
      issuer: cert.issuer,
      validFrom,
      validTo,
    };
  } catch (error) {
    trackException(error, { source: 'validateCertificate' });
    return null;
  }
}

/**
 * Clock-skew tolerance applied to certificate validity-window checks. Kept
 * small (5 minutes) on purpose: the *issuer* should backdate notBefore to
 * cover real-world latency — the Android client does this by 2 hours in
 * CertificateManager.kt, mirroring how production CAs (e.g. Let's Encrypt)
 * pre-date their certs. The server-side window only needs to absorb its
 * own NTP drift, not the issuer's clock.
 */
const CERT_VALIDITY_CLOCK_SKEW_MS = 5 * 60 * 1000;

/**
 * Validate certificate expiration dates
 * @param pemCert - PEM-encoded X.509 certificate
 * @returns Object with isValid flag and expiration info, or null on error
 */
export function validateCertificateExpiration(pemCert: string): {
  isValid: boolean;
  notBefore: Date;
  notAfter: Date;
  isExpired: boolean;
  isNotYetValid: boolean;
} | null {
  try {
    const cert = new crypto.X509Certificate(pemCert);

    // Get expiration dates from certificate
    const notBefore = new Date(cert.validFrom);
    const notAfter = new Date(cert.validTo);
    const now = new Date();

    // Check if certificate is expired or not yet valid, with a small
    // clock-skew tolerance applied symmetrically to both ends of the window.
    const isExpired = now.getTime() - CERT_VALIDITY_CLOCK_SKEW_MS > notAfter.getTime();
    const isNotYetValid = now.getTime() + CERT_VALIDITY_CLOCK_SKEW_MS < notBefore.getTime();
    const isValid = !isExpired && !isNotYetValid;

    return {
      isValid,
      notBefore,
      notAfter,
      isExpired,
      isNotYetValid,
    };
  } catch (error) {
    trackException(error, { source: 'validateCertificateExpiration' });
    return null;
  }
}

/**
 * Extract EC public key from X509 certificate and validate it's P-256
 * @param pemCert - Certificate in PEM format
 * @returns Public key in PEM format or null on error or if not P-256
 */
export function extractPublicKeyFromCert(pemCert: string): string | null {
  try {
    const cert = new crypto.X509Certificate(pemCert);

    // Validate it's an EC certificate
    if (cert.publicKey.asymmetricKeyType !== 'ec') {
      trackEvent('Certificate.PublicKeyRejected', { reason: 'NOT_EC' });
      return null;
    }

    // Export public key
    const publicKey = cert.publicKey.export({
      type: 'spki',
      format: 'pem',
    });

    // Validate curve is P-256 (prime256v1 / secp256r1)
    const keyObj = crypto.createPublicKey(publicKey);
    const keyDetails = keyObj.asymmetricKeyDetails;
    if (keyDetails?.namedCurve !== 'prime256v1') {
      trackEvent('Certificate.PublicKeyRejected', { reason: 'UNSUPPORTED_CURVE' });
      return null;
    }

    return publicKey.toString();
  } catch (error) {
    trackException(error, { source: 'extractPublicKeyFromCert' });
    return null;
  }
}

/**
 * Check if a certificate matches any of the pinned root CAs
 * Performs full byte-by-byte comparison for security
 * @param certDer - Certificate in DER format
 * @param pinnedCAs - Array of pinned CA certificates in PEM format
 * @returns True if certificate matches a pinned CA
 */
export function matchesPinnedCA(certDer: Buffer, pinnedCAs: string[]): boolean {
  try {
    for (const pinnedCA of pinnedCAs) {
      const pinnedDer = pemToDer(pinnedCA);

      // Full byte-by-byte comparison (not just hash comparison)
      if (certDer.equals(pinnedDer)) {
        return true;
      }
    }

    return false;
  } catch (error) {
    trackException(error, { source: 'matchesPinnedCA' });
    return false;
  }
}
