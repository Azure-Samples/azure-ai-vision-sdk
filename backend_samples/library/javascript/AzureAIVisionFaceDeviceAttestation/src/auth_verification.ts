/**
 * Auth verification dispatcher for iOS and Android attestation
 */

import { verifyiOSAuth, type AppAttestVerdict } from './ios';
import { verifyAndroidAuth, type PlayIntegrityVerdict } from './android';
import { trackEvent } from './logging';

/**
 * Verification result interface
 */
export interface AuthVerificationResult {
  verified: boolean;
  platform: string;
  message: string;
  timestamp: string;
  challengeHash?: string;
  clientId?: string;
  attestJsonLength?: number;
  chainLength?: number;
  rootCA?: string;
  integrityVerdict?: PlayIntegrityVerdict;
  appAttestVerdict?: AppAttestVerdict;
  leafCertValidityWarning?: string;
  warnings?: string[];
}

/**
 * Message data interface for attestation verification
 */
export interface AttestationMessageData {
  challengeHash?: string;
  clientId: string;
  system: string;
  publicCert?: string; // PEM-encoded leaf certificate
}

/**
 * Verify attestation JSON based on the platform system
 * @param messageData - Parsed message object containing challengeHash, clientId, system, publicCert
 * @param attestJson - The attestation JSON from the request
 * @returns Verification result object
 */
export async function verifyAuthBySystem(
  messageData: AttestationMessageData,
  attestJson: string
): Promise<AuthVerificationResult> {
  const systemLower = messageData.system.toLowerCase();

  trackEvent('AuthVerification.Dispatch', {
    platform: systemLower,
    clientId: messageData.clientId,
    attestJsonLength: attestJson.length,
  });

  if (systemLower === 'ios') {
    return await verifyiOSAuth(messageData, attestJson);
  } else if (systemLower === 'android') {
    return await verifyAndroidAuth(messageData, attestJson);
  } else {
    // This should never happen due to validation in the route handler
    trackEvent('AuthVerification.UnsupportedSystem', {
      platform: messageData.system,
      clientId: messageData.clientId,
    });
    return {
      verified: false,
      platform: 'unknown',
      message: `Unsupported system: ${messageData.system}`,
      timestamp: new Date().toISOString(),
    };
  }
}
