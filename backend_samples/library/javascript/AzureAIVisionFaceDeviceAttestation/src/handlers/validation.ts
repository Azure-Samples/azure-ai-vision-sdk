import { z } from 'zod';
import { fail } from './types';
import { sessionIdSchema } from '../server_utils';

export { sessionIdSchema } from '../server_utils';
const clientIdSchema = z.string({ error: issue => !issue.input ? 'MISSING_CLIENT_ID' : 'INVALID_CLIENT_ID' })
  .min(1, { error: 'MISSING_CLIENT_ID' })
  .refine(value => value.trim().length > 0, { error: 'INVALID_CLIENT_ID' });
const systemSchema = z.string({ error: issue => !issue.input ? 'MISSING_SYSTEM' : 'INVALID_SYSTEM' })
  .min(1, { error: 'MISSING_SYSTEM' }).toLowerCase()
  .pipe(z.enum(['ios', 'android'], { error: 'INVALID_SYSTEM' }));

export const identitySchema = z.object({ sessionId: sessionIdSchema, clientId: clientIdSchema, system: systemSchema });
const requiredBodyField = z.unknown().refine(Boolean, { error: 'MISSING_BODY_FIELDS' });
export const signedBodySchema = z.looseObject({
  payload: requiredBodyField, authPublicCert: requiredBodyField, signature: requiredBodyField,
}, { error: 'INVALID_JSON_BODY' }).pipe(z.looseObject({
  payload: z.string({ error: 'INVALID_PAYLOAD_FORMAT' })
    .refine(value => value.trim().length > 0, { error: 'INVALID_PAYLOAD_FORMAT' }),
  authPublicCert: z.string({ error: 'INVALID_CERT_PEM' }),
  signature: z.string({ error: 'MISSING_BODY_FIELDS' }),
}));
const payloadField = z.string({ error: 'MISSING_PAYLOAD_FIELDS' }).min(1, { error: 'MISSING_PAYLOAD_FIELDS' });
export const verificationPayloadSchema = z.looseObject({
  challengeHash: payloadField, encryptionPublicCert: payloadField,
}, { error: 'MISSING_PAYLOAD_FIELDS' });
export const registrationPayloadSchema = verificationPayloadSchema.extend({ attestJson: payloadField });
export const encryptedBodySchema = z.looseObject({
  encryptedData: z.string({ error: 'MISSING_ENCRYPTED_DATA' }).min(1, { error: 'MISSING_ENCRYPTED_DATA' }),
  signature: z.string({ error: 'MISSING_SIGNATURE' }).min(1, { error: 'MISSING_SIGNATURE' }),
}, { error: 'INVALID_JSON_BODY' });
const errors: Record<string, string> = {
  MISSING_SESSION_ID: 'Missing session ID',
  INVALID_SESSION_ID: 'Invalid session ID format',
  MISSING_CLIENT_ID: 'Missing client ID',
  INVALID_CLIENT_ID: 'Invalid client ID',
  MISSING_SYSTEM: 'Missing system parameter',
  INVALID_SYSTEM: 'Invalid system parameter. Must be "ios" or "android"',
  INVALID_JSON_BODY: 'Invalid JSON body',
  MISSING_BODY_FIELDS: 'Missing required fields: payload, authPublicCert, signature',
  INVALID_PAYLOAD_FORMAT: 'Invalid payload format. Expected JSON string',
  INVALID_CERT_PEM: 'Invalid certificate format. Expected PEM format',
  MISSING_PAYLOAD_FIELDS: 'Payload must contain challengeHash and encryptionPublicCert',
  MISSING_ENCRYPTED_DATA: 'Missing or invalid "encryptedData" field (expected base64 string)',
  MISSING_SIGNATURE: 'Missing or invalid "signature" field',
};

export function validateInput<Schema extends z.ZodType>(
  schema: Schema, input: unknown, route: string, properties: Record<string, unknown> = {},
  messages: Record<string, string> = {},
) {
  const result = schema.safeParse(input);
  if (result.success) return { success: true as const, data: result.data as z.output<Schema> };
  const code = result.error.issues[0].message;
  return { success: false as const, response: fail(route, 400, code, messages[code] ?? errors[code] ?? code, { properties }) };
}

export function validatePayload<Schema extends z.ZodType>(
  schema: Schema, payload: string, route: string, sessionId: string,
  missingFields = 'challengeHash and encryptionPublicCert',
) {
  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    return { success: false as const, response: fail(route, 400, 'PAYLOAD_JSON_PARSE_ERROR',
      'Invalid payload JSON format', { properties: { sid: sessionId } }) };
  }
  return validateInput(schema, parsed, route, { sid: sessionId }, {
    MISSING_PAYLOAD_FIELDS: `Payload must contain ${missingFields}`,
  });
}

export function validateIdentity(input: { sessionId: unknown; clientId: unknown; system: unknown }, route: string) {
  const result = validateInput(identitySchema, input, route, {
    ...(sessionIdSchema.safeParse(input.sessionId).success ? { sid: input.sessionId } : {}),
    ...(typeof input.system === 'string' && !systemSchema.safeParse(input.system).success && input.system
      ? { system: input.system.toLowerCase() } : {}),
  });
  return result;
}