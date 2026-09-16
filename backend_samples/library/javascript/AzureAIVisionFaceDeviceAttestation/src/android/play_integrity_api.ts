/**
 * Call Google's Play Integrity API to decode a client-supplied integrity
 * token into a verdict. Reads service-account credentials from
 * `GOOGLE_SERVICE_ACCOUNT_JSON` and the bundle id from `ANDROID_PACKAGE_NAME`.
 *
 * The semantic checks on the returned verdict (requestHash binding, timestamp
 * freshness, app + device integrity) live in `integrity_checks.ts`; this file
 * is just the network/credentials boundary.
 */
import { auth, playintegrity } from '@googleapis/playintegrity';
import {
  trackEvent,
  trackException,
  trackDependency,
} from '../logging';
import { getAttestationConfig } from '../config';
import type { PlayIntegrityVerdict } from './types';

/**
 * Outcome of a Play Integrity decode attempt.
 *
 * `ok: false` with `tolerable: true` is reserved for the two conditions the
 * `allowAndroidAttestationWhenGoogleUnavailable` policy may accept: the Play
 * Integrity API was unreachable (network error / HTTP 5xx) or the decode quota
 * was exhausted (HTTP 429). Every other failure — missing/invalid credentials,
 * wrong package, an empty or rejected token — is `tolerable: false` and must
 * always fail closed.
 */
export type IntegrityVerdictResult =
  | { ok: true; verdict: PlayIntegrityVerdict }
  | { ok: false; tolerable: boolean; reason: string };

/**
 * Classify a decode error as a tolerable Google-unavailability condition
 * (`server_unavailable` for a network error / HTTP 5xx, `quota_exceeded` for
 * HTTP 429) or `null` when it is a hard failure that must fail closed.
 */
function classifyUnavailable(
  error: unknown,
): 'server_unavailable' | 'quota_exceeded' | null {
  if (!error || typeof error !== 'object') return null;
  const e = error as {
    code?: string | number;
    status?: number;
    response?: { status?: number };
    message?: string;
  };
  const httpStatus =
    typeof e.response?.status === 'number'
      ? e.response.status
      : typeof e.status === 'number'
        ? e.status
        : typeof e.code === 'number'
          ? e.code
          : undefined;

  // Quota / rate limit exceeded → HTTP 429 (RESOURCE_EXHAUSTED).
  if (httpStatus === 429) return 'quota_exceeded';

  // Google server-side error → HTTP 5xx.
  if (httpStatus !== undefined && httpStatus >= 500 && httpStatus <= 599) {
    return 'server_unavailable';
  }

  // No HTTP response → network / connectivity failure reaching Google.
  if (httpStatus === undefined) {
    const code = typeof e.code === 'string' ? e.code : '';
    const NETWORK_ERROR_CODES = [
      'ECONNREFUSED', 'ECONNRESET', 'ECONNABORTED', 'ETIMEDOUT',
      'ENOTFOUND', 'EAI_AGAIN', 'EPIPE', 'ENETUNREACH', 'EHOSTUNREACH',
    ];
    if (NETWORK_ERROR_CODES.includes(code)) return 'server_unavailable';
    const msg = (e.message || '').toLowerCase();
    if (msg.includes('timeout') || msg.includes('network') || msg.includes('socket hang up')) {
      return 'server_unavailable';
    }
  }

  return null;
}

/**
 * Decrypt and verify a Google Play Integrity token into a verdict.
 *
 * Returns a discriminated {@link IntegrityVerdictResult}: a verdict on success,
 * or `{ ok: false, tolerable }` on failure, where `tolerable` marks the
 * Google-unavailable / quota-exceeded conditions the caller may accept when
 * `allowAndroidAttestationWhenGoogleUnavailable` is set.
 *
 * @param integrityToken - The integrity token from the client
 */
export async function decryptAndVerifyIntegrityVerdict(
  integrityToken: string,
): Promise<IntegrityVerdictResult> {
  // Get service account credentials from environment variable
  const serviceAccountJson = getAttestationConfig().googleServiceAccountJson;
  if (!serviceAccountJson) {
    trackEvent('AndroidAuth.PlayIntegrity.ConfigMissing', {
      missing: 'GOOGLE_SERVICE_ACCOUNT_JSON',
    });
    return { ok: false, tolerable: false, reason: 'service_account_not_configured' };
  }

  // Parse service account credentials
  let credentials;
  try {
    credentials = JSON.parse(serviceAccountJson);
  } catch (error) {
    trackException(error, { source: 'decryptAndVerifyIntegrityVerdict.parseCreds' });
    return { ok: false, tolerable: false, reason: 'service_account_parse_error' };
  }

  // Use the Android package name for the liveness app
  const packageName = getAttestationConfig().androidPackageName;
  if (!packageName) {
    trackEvent('AndroidAuth.PlayIntegrity.ConfigMissing', {
      missing: 'ANDROID_PACKAGE_NAME',
    });
    return { ok: false, tolerable: false, reason: 'package_name_not_configured' };
  }

  const depStart = Date.now();
  try {
    // Create auth client with service account
    const authClient = new auth.GoogleAuth({
      credentials,
      scopes: ['https://www.googleapis.com/auth/playintegrity'],
    });

    // Create Play Integrity API client
    const client = playintegrity({
      version: 'v1',
      auth: authClient,
    });

    // Decrypt the integrity token
    trackEvent('AndroidAuth.PlayIntegrity.DecodeStarted');
    const response = await client.v1.decodeIntegrityToken({
      packageName,
      requestBody: {
        integrityToken,
      },
    });
    const depDuration = Date.now() - depStart;

    if (!response.data || !response.data.tokenPayloadExternal) {
      trackDependency({
        name: 'PlayIntegrity.decodeIntegrityToken',
        target: 'playintegrity.googleapis.com',
        data: `packageName=${packageName}`,
        duration: depDuration,
        success: false,
        resultCode: 'empty-response',
        properties: { packageName },
      });
      return { ok: false, tolerable: false, reason: 'empty_response' };
    }

    // Extract and parse the tokenPayloadExternal
    const verdict = response.data.tokenPayloadExternal as PlayIntegrityVerdict;

    trackDependency({
      name: 'PlayIntegrity.decodeIntegrityToken',
      target: 'playintegrity.googleapis.com',
      data: `packageName=${packageName}`,
      duration: depDuration,
      success: true,
      resultCode: 200,
      properties: {
        packageName,
        appRecognition: verdict.appIntegrity?.appRecognitionVerdict,
        deviceRecognition: verdict.deviceIntegrity?.deviceRecognitionVerdict?.join(','),
        playProtect: verdict.environmentDetails?.playProtectVerdict,
      },
    });

    return { ok: true, verdict };
  } catch (error: unknown) {
    const depDuration = Date.now() - depStart;
    let status: number | string = 'exception';
    let message: string | undefined;

    // Handle Google API errors
    if (error && typeof error === 'object' && 'response' in error) {
      const apiError = error as {
        response?: {
          status?: number;
          statusText?: string;
          data?: unknown;
        };
        message?: string;
      };

      status = apiError.response?.status ?? 'exception';
      message = apiError.message;
    } else if (error instanceof Error) {
      message = error.message;
    }

    trackDependency({
      name: 'PlayIntegrity.decodeIntegrityToken',
      target: 'playintegrity.googleapis.com',
      data: `packageName=${packageName}`,
      duration: depDuration,
      success: false,
      resultCode: status,
      properties: { packageName, message },
    });
    trackException(new Error(message || 'Play Integrity API request failed'), {
      source: 'decryptAndVerifyIntegrityVerdict',
      packageName,
      status,
    });
    const unavailable = classifyUnavailable(error);
    if (unavailable) {
      return { ok: false, tolerable: true, reason: unavailable };
    }
    return { ok: false, tolerable: false, reason: 'api_error' };
  }
}
