/**
 * Decoders for the two CBOR payloads Apple's App Attest APIs return, plus the
 * credCert nonce-extension extractor.
 *
 *   parseAppAttestToken      — DCAppAttestService.attestKey → AppAttestObject
 *   parseAppAttestAssertion  — DCAppAttestService.generateAssertion → AppAttestAssertionObject
 *   extractNonceFromCredCert — pulls the nonce OCTET STRING out of credCert
 *                              extension OID 1.2.840.113635.100.8.2
 */
import { Constructed, fromBER, OctetString, Sequence } from 'asn1js';
import { z } from 'zod';
import { getExtensionValue } from '../cert_utils';
import { AUTH_DATA_HEADER_BYTES, FLAGS_OFFSET, NONCE_OID, RP_ID_HASH_BYTES, SIGN_COUNT_OFFSET } from './constants';
import { decodeCbor } from './cbor';
import type {
  AppAttestObject,
  AppAttestAssertionObject,
} from './types';

const byteStringSchema = z.custom<Buffer>(Buffer.isBuffer, { error: 'expected a CBOR byte string' });
const cborMapSchema = z.instanceof(Map).transform(value => Object.fromEntries(value));
const appAttestSchema = cborMapSchema.pipe(z.object({
  fmt: z.string(),
  authData: byteStringSchema,
  attStmt: cborMapSchema.pipe(z.object({
    x5c: z.tuple([byteStringSchema, byteStringSchema]).rest(z.unknown()),
    receipt: byteStringSchema.nullable().catch(null),
  })),
}));
const appAssertionSchema = cborMapSchema.pipe(z.object({
  signature: byteStringSchema,
  authenticatorData: byteStringSchema.refine(value => value.length >= AUTH_DATA_HEADER_BYTES, {
    error: `assertion authenticatorData must contain at least ${AUTH_DATA_HEADER_BYTES} bytes`,
  }),
}));

/**
 * Decode the base64-CBOR attestation token returned by
 * DCAppAttestService.attestKey. Validates the outer shape Apple documents
 * (fmt + attStmt{x5c, receipt?} + authData) and pulls out the two certs from
 * x5c (credCert + intermediate).
 */
export function parseAppAttestToken(tokenB64: string): AppAttestObject {
  const tokenBuf = Buffer.from(tokenB64, 'base64');
  const [topRaw] = decodeCbor(tokenBuf);
  const { fmt, authData, attStmt: { x5c: [credCertDer, intermediateDer], receipt } } = appAttestSchema.parse(topRaw);
  return { fmt, authData, credCertDer, intermediateDer, receiptLength: receipt?.length ?? 0, receipt };
}

/**
 * Decode the base64-CBOR assertion returned by
 * DCAppAttestService.generateAssertion. Validates signature + authenticatorData
 * are present and that authenticatorData is at least 37 bytes (rpIdHash || flags
 * || signCount).
 */
export function parseAppAttestAssertion(assertionB64: string): AppAttestAssertionObject {
  const buf = Buffer.from(assertionB64, 'base64');
  const [topRaw] = decodeCbor(buf);
  return appAssertionSchema.parse(topRaw);
}

/**
 * Decode the first 37 bytes of an assertion's `authenticatorData`:
 *   rpIdHash (32) || flags (1) || signCount (4)
 *
 * Used by `verifyAssertionAgainstAuthCert` (replaces hand-rolled offset reads)
 * and by `inspectAppAttest` for display.
 */
export function parseAssertionAuthData(authenticatorData: Buffer): {
  rpIdHash: Buffer;
  flags: number;
  signCount: number;
} {
  if (authenticatorData.length < AUTH_DATA_HEADER_BYTES) {
    throw new Error(`authenticatorData is ${authenticatorData.length} bytes, < ${AUTH_DATA_HEADER_BYTES}`);
  }
  return {
    rpIdHash: authenticatorData.subarray(0, RP_ID_HASH_BYTES),
    flags: authenticatorData[FLAGS_OFFSET],
    signCount: authenticatorData.readUInt32BE(SIGN_COUNT_OFFSET),
  };
}

/**
 * Walk into the DER bytes of the credCert and pull out the OCTET STRING value
 * embedded in the App Attest nonce extension (OID 1.2.840.113635.100.8.2).
 * Structure:
 *   Extension ::= SEQUENCE { extnID OID, critical BOOLEAN DEFAULT FALSE, extnValue OCTET STRING }
 *   extnValue (DER) = SEQUENCE { [1] EXPLICIT OCTET STRING nonce }
 */
export function extractNonceFromCredCert(credCertDer: Buffer): Buffer | null {
  const container = getExtensionValue(credCertDer, NONCE_OID);
  if (!container) return null;
  try {
    const decoded = fromBER(container);
    if (decoded.offset !== container.length || !(decoded.result instanceof Sequence)) return null;
    const fields = decoded.result.valueBlock.value;
    const tagged = fields[0];
    if (fields.length !== 1 || !(tagged instanceof Constructed) ||
        tagged.idBlock.tagClass !== 3 || tagged.idBlock.tagNumber !== 1) return null;
    const [nonce] = tagged.valueBlock.value;
    if (tagged.valueBlock.value.length !== 1 || !(nonce instanceof OctetString) || nonce.idBlock.isConstructed) return null;
    return Buffer.from(nonce.valueBlock.valueHexView);
  } catch {
    return null;
  }
}
