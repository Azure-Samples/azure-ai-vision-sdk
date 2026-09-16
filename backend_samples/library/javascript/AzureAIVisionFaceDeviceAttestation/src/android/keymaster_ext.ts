/**
 * Android Key Attestation extension parsing.
 *
 * The leaf cert in the attestation chain carries a Keymaster/KeyMint extension
 * (OID 1.3.6.1.4.1.11129.2.1.17) whose payload is a `KeyDescription` SEQUENCE.
 * We parse the five leading fields — attestationVersion, attestationSecurityLevel,
 * keyMintVersion, keyMintSecurityLevel, attestationChallenge. The
 * `attestationChallenge` is the byte string the client passed to KeyStore as
 * the attestation nonce; it must equal the session challengeHash to bind the
 * chain to *this* session and reject pre-rotation chains from earlier sessions.
 */
import { Enumerated, fromBER, Integer, OctetString, Sequence } from 'asn1js';
import { getExtensionValue } from '../cert_utils';

export const KEYMASTER_EXT_OID = '1.3.6.1.4.1.11129.2.1.17';

/**
 * Decoded leading fields of the `KeyDescription` SEQUENCE inside the Keymaster
 * extension. We only parse the first five (fixed-position) — the trailing
 * softwareEnforced / hardwareEnforced AuthorizationList SEQUENCEs are not
 * needed by the verifier.
 */
export interface KeyDescription {
  attestationVersion: number;
  attestationSecurityLevel: number;
  keyMintVersion: number;
  keyMintSecurityLevel: number;
  /** Bytes from the OCTET STRING — for the verifier this is the session nonce. */
  attestationChallenge: Buffer;
}

/**
 * Walk the leaf cert DER, locate the Android Key Attestation extension
 * (OID 1.3.6.1.4.1.11129.2.1.17), and decode the five leading fields of the
 * inner `KeyDescription` SEQUENCE.
 *
 * Structure (per https://source.android.com/docs/security/features/keystore/attestation):
 *   Extension ::= SEQUENCE { extnID OID, critical BOOLEAN DEFAULT FALSE, extnValue OCTET STRING }
 *   extnValue (DER) = SEQUENCE {
 *     attestationVersion       INTEGER,
 *     attestationSecurityLevel ENUMERATED,
 *     keyMintVersion           INTEGER,
 *     keyMintSecurityLevel     ENUMERATED,
 *     attestationChallenge     OCTET STRING,
 *     ...                      -- uniqueId + AuthorizationLists, ignored
 *   }
 *
 * Returns null on any parse failure so the caller can emit a single
 * `KEYMASTER_EXT_MISSING` reason.
 */
export function parseKeyDescription(leafDer: Buffer): KeyDescription | null {
  const container = getExtensionValue(leafDer, KEYMASTER_EXT_OID);
  if (!container) return null;
  try {
    const decoded = fromBER(container);
    if (decoded.offset !== container.length || !(decoded.result instanceof Sequence)) return null;
    const [version, security, keyVersion, keySecurity, challenge] = decoded.result.valueBlock.value;
    if (!(version instanceof Integer) || version.idBlock.tagNumber !== 2 ||
        !(security instanceof Enumerated) ||
        !(keyVersion instanceof Integer) || keyVersion.idBlock.tagNumber !== 2 ||
        !(keySecurity instanceof Enumerated) ||
        !(challenge instanceof OctetString) || challenge.idBlock.isConstructed) return null;
    const values = [version, security, keyVersion, keySecurity].map(value => Number(value.toBigInt()));
    if (values.some(value => !Number.isSafeInteger(value) || value < 0)) return null;
    return {
      attestationVersion: values[0],
      attestationSecurityLevel: values[1],
      keyMintVersion: values[2],
      keyMintSecurityLevel: values[3],
      attestationChallenge: Buffer.from(challenge.valueBlock.valueHexView),
    };
  } catch {
    return null;
  }
}

/**
 * Convenience accessor used by the chain verifier — returns just the
 * `attestationChallenge` OCTET STRING bytes from the Keymaster extension on
 * the leaf cert. Kept as a one-liner over `parseKeyDescription` so production
 * and the inspector share the same parser.
 */
export function extractAttestationChallengeFromCert(leafDer: Buffer): Buffer | null {
  return parseKeyDescription(leafDer)?.attestationChallenge ?? null;
}

export function isHardwareAttestationSecurityLevel(
  description: KeyDescription | null,
): boolean {
  return description?.attestationSecurityLevel === 1 ||
    description?.attestationSecurityLevel === 2;
}
