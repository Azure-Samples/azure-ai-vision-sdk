import GenerateForm from './GenerateForm';

// `/` only serves the token-generation form. The session landing page lives at
// `/native` (see app/native/page.tsx); a `?s=` here is ignored.
export const dynamic = 'force-dynamic';

// Deep-link + App Attest / Play Integrity env vars surfaced on the index page so
// you can see at a glance what's wired up. Only *presence* is shown — never the
// value — so no secret (e.g. the service-account JSON) is leaked to the browser.
const REQUIRED_VARS: ReadonlyArray<readonly [string, string, string]> = [
  [
    'IOS_APP_ID',
    'iOS App Attest: TeamID.BundleID the App Attest attestation/assertion must match.',
    'https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server',
  ],
  [
    'IOS_APP_CLIP_ID',
    'iOS App Clip appID written into the AASA appclips section.',
    'https://developer.apple.com/documentation/xcode/supporting-associated-domains',
  ],
  [
    'IOS_APPLINK_APP_ID',
    'Universal Link AASA appIDs binding the domain to the iOS app (falls back to IOS_APP_ID).',
    'https://developer.apple.com/documentation/xcode/supporting-associated-domains',
  ],
  [
    'ANDROID_PACKAGE_NAME',
    'Android applicationId used for Play Integrity checks and the assetlinks binding.',
    'https://developer.android.com/google/play/integrity/overview',
  ],
  [
    'ANDROID_SHA256_CERT_FINGERPRINTS',
    'App Link assetlinks signing-cert SHA-256 fingerprints.',
    'https://developers.google.com/digital-asset-links/v1/getting-started',
  ],
  [
    'GOOGLE_SERVICE_ACCOUNT_JSON',
    'Credentials used to call the Play Integrity API (secret \u2014 value never shown).',
    'https://developer.android.com/google/play/integrity/setup',
  ],
  [
    'APPLINK_PATH',
    'Universal/App Link path pattern (defaults to /native* when unset).',
    'https://developer.apple.com/documentation/xcode/supporting-associated-domains',
  ],
];

const OPTIONAL_VARS: ReadonlyArray<readonly [string, string, string]> = [
  [
    'DEBUG_MODE',
    'Relaxes dev attestation policy: allows the App Attest "appattestdevelop" aaguid and Android UNRECOGNIZED_VERSION / weaker integrity.',
    'https://developer.android.com/google/play/integrity/verdicts',
  ],
  [
    'ALLOW_DEVICE_INTEGRITY',
    'Accept Play Integrity MEETS_DEVICE_INTEGRITY (not only STRONG) as a pass.',
    'https://developer.android.com/google/play/integrity/verdicts',
  ],
  [
    'ALLOW_BASIC_INTEGRITY',
    'Accept Play Integrity MEETS_BASIC_INTEGRITY (not only STRONG) as a pass.',
    'https://developer.android.com/google/play/integrity/verdicts',
  ],
  [
    'ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE',
    'Allow Android attestation to pass on hardware Key Attestation alone when the Play Integrity API is unavailable (default false = fail closed).',
    'https://developer.android.com/google/play/integrity/verdicts',
  ],
  [
    'IOS_APP_STORE_URL',
    'App Store link on the landing page; also the iOS App Clip URL (receives ?s=<sessionId>).',
    '',
  ],
  ['ANDROID_PLAY_STORE_URL', 'Google Play link shown on the landing page.', ''],
];

interface VarStatus {
  name: string;
  isSet: boolean;
  purpose: string;
  docUrl: string;
}

function varStatus(vars: ReadonlyArray<readonly [string, string, string]>): VarStatus[] {
  return vars.map(([name, purpose, docUrl]) => ({
    name,
    isSet: Boolean((process.env[name] || '').trim()),
    purpose,
    docUrl,
  }));
}

function ConfigRows({ items, requiredGroup }: { items: VarStatus[]; requiredGroup: boolean }) {
  return (
    <>
      {items.map(({ name, isSet, purpose, docUrl }) => {
        const pill = isSet ? 'ok' : requiredGroup ? 'miss' : 'muted';
        const label = isSet ? 'Set' : 'Not set';
        return (
          <tr key={name}>
            <td className="cfg-name">{name}</td>
            <td>
              <span className={`pill ${pill}`}>{label}</span>
            </td>
            <td className="cfg-use">
              {purpose}
              {docUrl ? (
                <>
                  {' '}
                  <a className="cfg-doc" href={docUrl} target="_blank" rel="noopener noreferrer">
                    Docs &#8599;
                  </a>
                </>
              ) : null}
            </td>
          </tr>
        );
      })}
    </>
  );
}

export default function Page() {
  const required = varStatus(REQUIRED_VARS);
  const optional = varStatus(OPTIONAL_VARS);
  const setCount = required.filter((r) => r.isSet).length;
  const total = required.length;
  const missing = required.filter((r) => !r.isSet).map((r) => r.name);
  const missingNote = missing.length
    ? 'Not configured (required): ' + missing.join(', ')
    : 'All required deep-link / attestation variables are configured.';

  const config = (
    <details className="config" open={missing.length > 0}>
      <summary>
        Deep-link &amp; attestation config &mdash; {setCount}/{total} required set
      </summary>
      <p className="hint">{missingNote}</p>
      <table className="cfg">
        <tbody>
          <tr className="cfg-group">
            <td colSpan={3}>Required</td>
          </tr>
          <ConfigRows items={required} requiredGroup />
          <tr className="cfg-group">
            <td colSpan={3}>Optional</td>
          </tr>
          <ConfigRows items={optional} requiredGroup={false} />
        </tbody>
      </table>
      <p className="hint">
        Only whether each variable is set is shown &mdash; values (including the service-account
        JSON) are never exposed.
      </p>
    </details>
  );

  return (
    <div className="index-page">
      <main className="card">
        <h1>Start a Liveness Session</h1>
        <p className="help">
          Enter your Azure Face resource and API key to generate a session token. You&apos;ll be
          redirected into the liveness launch page.
        </p>
        <GenerateForm config={config} />
      </main>
    </div>
  );
}
