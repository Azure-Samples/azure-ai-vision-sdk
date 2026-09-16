/**
 * App-side wiring for the attestation library.
 *
 * Sources the {@link AttestationConfig} from environment variables (the library
 * itself never reads env), wires in the app's {@link getAttestationStore} backend,
 * and exposes a process-wide singleton {@link AttestationService}. Routes call
 * `getAttestationService().<method>(...)`.
 */
import {
  createAttestationService,
  type AttestationConfig,
  type AttestationLogger,
  type AttestationService,
} from '@azure/ai-vision-face-deviceattestation';
import { getAttestationStore } from './store';
import { trackEvent, trackException, trackDependency } from './app_insights_server';
import { logDebugBannerOnce } from './debug_banner';

/** Split a comma/space separated fingerprint list into a trimmed, non-empty array. */
function splitFingerprints(value: string | undefined | null): string[] {
  if (!value) return [];
  return value
    .replace(/,/g, ' ')
    .split(/\s+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

/** Build the attestation config from environment variables. */
function buildConfig(): AttestationConfig {
  return {
    maxCertSize: parseInt(process.env.MAX_CERT_SIZE || '10240', 10),
    androidPackageName: process.env.ANDROID_PACKAGE_NAME || '',
    googleServiceAccountJson: process.env.GOOGLE_SERVICE_ACCOUNT_JSON || '',
    debugMode: process.env.DEBUG_MODE === 'true',
    allowDeviceIntegrity: process.env.ALLOW_DEVICE_INTEGRITY === 'true',
    allowBasicIntegrity: process.env.ALLOW_BASIC_INTEGRITY === 'true',
    allowAndroidAttestationWhenGoogleUnavailable:
      process.env.ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE === 'true',
    iosAppId: process.env.IOS_APP_ID || '',
    iosApplinkAppId: process.env.IOS_APPLINK_APP_ID,
    iosAppClipId: process.env.IOS_APP_CLIP_ID,
    androidSha256CertFingerprints: splitFingerprints(process.env.ANDROID_SHA256_CERT_FINGERPRINTS),
    applinkPath: process.env.APPLINK_PATH || '/native*',
  };
}

/** App Insights implementation of the library's telemetry logger. */
const appInsightsLogger: AttestationLogger = { trackEvent, trackException, trackDependency };

/** Readable local default when the sample has no App Insights configuration. */
const consoleLogger: AttestationLogger = {
  trackEvent(name, properties, measurements) {
    console.log(`[Attestation] ${name}`, {
      ...(properties ?? {}),
      ...(measurements && { measurements }),
    });
  },
  trackException(error, properties) {
    const exception = error instanceof Error
      ? { name: error.name, message: error.message, stack: error.stack }
      : { message: String(error) };
    console.log('[Attestation] Exception', { ...properties, exception });
  },
  trackDependency({ properties, ...dependency }) {
    console.log(`[Attestation] Dependency ${dependency.name}`, {
      ...dependency,
      ...properties,
    });
  },
};

const attestationLogger =
  process.env.APPLICATIONINSIGHTS_CONNECTION_STRING ||
  process.env.NEXT_PUBLIC_APPINSIGHTS_INSTRUMENTATION_KEY
    ? appInsightsLogger
    : consoleLogger;

let service: AttestationService | null = null;

/** Process-wide singleton attestation service (config + Redis store + host logger). */
export function getAttestationService(): AttestationService {
  if (!service) {
    logDebugBannerOnce();
    service = createAttestationService(buildConfig(), getAttestationStore(), attestationLogger);
  }
  return service;
}
