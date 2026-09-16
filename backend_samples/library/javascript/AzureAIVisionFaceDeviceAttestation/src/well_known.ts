/**
 * App Link / Universal Link binding documents served at /.well-known/*.
 *
 * Driven entirely by the injected {@link AttestationConfig} (no environment
 * variables), so the library binds to any domain / app identity purely through
 * configuration.
 */
import { getAttestationConfig } from './config';

/** Universal/App Link path pattern (config.applinkPath, default /native*). */
export function applinkPath(): string {
  return getAttestationConfig().applinkPath;
}

/** Build the AASA document iOS fetches from /.well-known to bind the domain. */
export function appleAppSiteAssociation(): Record<string, unknown> {
  const cfg = getAttestationConfig();
  const appId = (cfg.iosApplinkAppId || cfg.iosAppId || '').trim();
  const clipId = (cfg.iosAppClipId || '').trim();

  const appIds: string[] = [];
  if (appId) appIds.push(appId);
  if (clipId) appIds.push(clipId);

  const doc: Record<string, unknown> = {
    applinks: {
      details: [
        {
          appIDs: appIds,
          components: [
            {
              '/': applinkPath(),
              comment: 'Opens the app for liveness sessions.',
            },
          ],
        },
      ],
    },
  };
  if (clipId) {
    doc.appclips = { apps: [clipId] };
  }
  return doc;
}

/** Build the Digital Asset Links document Android fetches to bind the domain. */
export function assetlinks(): Array<Record<string, unknown>> {
  const cfg = getAttestationConfig();
  const packageName = (cfg.androidPackageName || '').trim();
  const fingerprints = cfg.androidSha256CertFingerprints;

  return [
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
  ];
}
