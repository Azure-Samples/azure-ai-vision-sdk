/**
 * One-time process-level warning when any develop-build / debug env flag is
 * set. The flags relax production policy (accept Apple's `appattestdevelop`
 * aaguid, non-STRONG Android device integrity, etc.) — we log them prominently
 * so it's obvious from server logs that a deployment is running with relaxed
 * checks.
 *
 * `logDebugBannerOnce` is called from the attestation verifiers so the banner
 * fires lazily on first verify (after env is loaded) and only once per
 * process.
 */
let bannerLogged = false;

interface DebugFlag {
  name: string;
  active: boolean;
  detail: string;
}

function collectDebugFlags(): DebugFlag[] {
  return [
    {
      name: 'DEBUG_MODE',
      active: process.env.DEBUG_MODE === 'true',
      detail: 'iOS appattestdevelop aaguid accepted; Android UNRECOGNIZED_VERSION + MEETS_DEVICE_INTEGRITY / MEETS_BASIC_INTEGRITY accepted (cert validity is NOT relaxed)',
    },
    {
      name: 'ALLOW_DEVICE_INTEGRITY',
      active: process.env.ALLOW_DEVICE_INTEGRITY === 'true',
      detail: 'Android MEETS_DEVICE_INTEGRITY accepted (not just MEETS_STRONG_INTEGRITY)',
    },
    {
      name: 'ALLOW_BASIC_INTEGRITY',
      active: process.env.ALLOW_BASIC_INTEGRITY === 'true',
      detail: 'Android MEETS_BASIC_INTEGRITY accepted',
    },
    {
      name: 'ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE',
      active: process.env.ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE === 'true',
      detail: 'Android attestation accepted on hardware Key Attestation alone when the Play Integrity API is unavailable',
    },
    {
      name: 'USE_LOCAL_REDIS',
      active: process.env.USE_LOCAL_REDIS === 'true',
      detail: 'Redis connection bypasses Azure Managed Identity + TLS',
    },
  ];
}

export function logDebugBannerOnce(): void {
  if (bannerLogged) return;
  bannerLogged = true;

  const active = collectDebugFlags().filter((f) => f.active);
  if (active.length === 0) return;

  console.warn('================ DEBUG / DEV FLAGS ACTIVE ================');
  for (const f of active) {
    console.warn(`[WARN] ${f.name}: ${f.detail}`);
  }
  console.warn('Develop builds will be accepted. Do NOT enable in production.');
  console.warn('==========================================================');
}
