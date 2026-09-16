/**
 * Server-side helpers for the deep-link landing page (port of the landing bits
 * of the Python app/web/session.py). Builds the QR code, the platform-specific
 * "open in app" action, and the static step-by-step flow guide.
 *
 * Server-only: imports `qrcode` and reads env vars. Never import from a client
 * component.
 */
import QRCode from 'qrcode';
import type { Metadata } from 'next';

/** Escape a string for safe interpolation into HTML text / attribute values. */
function escapeHtml(s: string): string {
  return s.replace(
    /[&<>"']/g,
    (c) =>
      (({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' } as Record<
        string,
        string
      >)[c])
  );
}

/** Classify the requesting client as 'android', 'ios' or 'desktop' from its UA. */
export function detectPlatform(userAgent: string): 'android' | 'ios' | 'desktop' {
  const ua = (userAgent || '').toLowerCase();
  if (ua.includes('android')) return 'android';
  if (['iphone', 'ipad', 'ipod'].some((t) => ua.includes(t))) return 'ios';
  // iPadOS Safari masquerades as desktop macOS but reports a touch UA.
  if (ua.includes('macintosh') && ua.includes('mobile')) return 'ios';
  return 'desktop';
}

/** Append non-empty query params to `url`, preserving any it already has. */
function appendQuery(url: string, params: Record<string, string>): string {
  const extra = Object.entries(params).filter(([, v]) => v);
  if (!url || extra.length === 0) return url;
  const sep = url.includes('?') ? '&' : '?';
  const qs = extra
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  return `${url}${sep}${qs}`;
}

/**
 * Build an Android `intent://` URI that force-opens the installed app.
 *
 * A same-origin `<a href="https://...">` click does NOT hand off to a verified
 * App Link app (Chrome treats it as in-page navigation). An explicit Intent URI
 * naming the app `package` makes Android launch the app directly, with
 * `S.browser_fallback_url` covering the not-installed case. The scheme is pinned
 * to `https` because the app's intent-filter is https-only and a TLS-terminating
 * proxy can otherwise make the request look like http.
 */
function androidIntentUrl(httpsUrl: string, pkg: string, fallbackUrl = ''): string {
  const u = new URL(httpsUrl);
  let target = u.host + u.pathname;
  if (u.search) target += u.search;
  let fragment = `Intent;scheme=https;package=${pkg};`;
  if (fallbackUrl) {
    fragment += `S.browser_fallback_url=${encodeURIComponent(fallbackUrl)};`;
  }
  fragment += 'end';
  return `intent://${target}#${fragment}`;
}

/**
 * Return `url` with its path swapped for `path`, scheme pinned to https, and the
 * query rebuilt from scratch with only `s=<sessionId>`. Lets the QR / "open in
 * app" target point at /native (intercepted by the installed app) while the
 * post-check callbackUrl points at /result (which never matches the App Link
 * filter, so the return navigation always lands in a browser).
 */
function withPath(url: string, path: string, sessionId = ''): string {
  const u = new URL(url);
  u.protocol = 'https:';
  u.pathname = path;
  u.search = sessionId ? `?s=${encodeURIComponent(sessionId)}` : '';
  u.hash = '';
  return u.toString();
}

/** Render `data` as a self-contained SVG QR code (no external service). */
async function qrSvg(data: string): Promise<string> {
  return QRCode.toString(data, {
    type: 'svg',
    errorCorrectionLevel: 'M',
    margin: 2,
    color: { dark: '#0f1220', light: '#ffffff' },
  });
}

function storeLink(href: string, label: string, cls = 'store'): string {
  if (!href) return '';
  return `<a class="${cls}" href="${escapeHtml(href)}">${label}</a>`;
}

export interface LandingFragments {
  qrHtml: string;
  actionHtml: string;
  guideHtml: string;
  launchHtml: string;
}

/**
 * Compute the landing-page HTML fragments for a session: the QR code, the
 * platform-specific action block, the advanced launch info, and the static flow
 * guide. Injected into the client landing component via dangerouslySetInnerHTML.
 */
export async function buildLanding(
  sessionId: string,
  pageUrl: string,
  userAgent: string
): Promise<LandingFragments> {
  const iosStore = process.env.IOS_APP_STORE_URL || '';
  const playStore = process.env.ANDROID_PLAY_STORE_URL || '';
  const platform = detectPlatform(userAgent);

  const nativeUrl = pageUrl ? withPath(pageUrl, '/native', sessionId) : '';
  const resultUrl = pageUrl ? withPath(pageUrl, '/result', sessionId) : '';
  // The backend host this instance serves. The App Clip is launched via the
  // App Store / App Clip URL (whose host is apps.apple.com, not the backend), so
  // it can't derive the backend host from its own launch URL — it is passed as
  // the `domain` query parameter below and validated against the App Clip's
  // built-in whitelist before use.
  const backendHost = pageUrl ? new URL(pageUrl).hostname : '';
  // The QR opens the /native App Link but carries callbackUrl=/result so a phone
  // that scans it returns to the browser result page after the check.
  const qrUrl = nativeUrl ? appendQuery(nativeUrl, { callbackUrl: resultUrl }) : '';

  const qrHtml = qrUrl
    ? `<div class="qr">${await qrSvg(qrUrl)}<p class="qr-hint">${escapeHtml(
        'Scan with your phone to open the app.'
      )}</p></div>`
    : '';

  let actionHtml: string;
  if (platform === 'android') {
    // Explicit Android Intent URI naming the app package so the installed app
    // launches directly; if it isn't installed, browser_fallback_url sends the
    // user to the Play Store (or back to this page). callbackUrl points at
    // /result so the app returns to a plain page (never the App Link).
    const landingUrl = appendQuery(nativeUrl, { callbackUrl: resultUrl });
    const pkg = (process.env.ANDROID_PACKAGE_NAME || '').trim();
    let openUrl: string;
    if (pkg && landingUrl) {
      // Attach the same URL the QR encodes as a Play Install Referrer so, after
      // install + first launch, the app can resume this exact session.
      const playFallback = playStore
        ? appendQuery(playStore, { referrer: landingUrl })
        : landingUrl;
      openUrl = androidIntentUrl(landingUrl, pkg, playFallback);
    } else {
      openUrl = landingUrl;
    }
    const openApp = storeLink(openUrl, 'Open in app', 'store open-app');
    actionHtml =
      "<p>Open the app to run the check now. If it isn't installed yet, this takes you to Google Play.</p>" +
      `<div>${openApp}</div>`;
  } else if (platform === 'ios') {
    // On iOS the App Store URL doubles as the App Clip URL. Pass the session id
    // so the App Clip receives it on launch, callbackUrl (the /result page) so
    // the App Clip returns to a browser to show the result once it's done, and
    // domain (this backend's host) so the App Clip knows which liveness backend
    // to talk to — its launch URL host is the App Store's, not ours.
    const clipUrl = appendQuery(iosStore, {
      s: sessionId,
      callbackUrl: resultUrl,
      domain: backendHost,
    });
    actionHtml =
      '<p>Open the App Clip to run the check now, or get the full app from the App Store.</p>' +
      `<div>${storeLink(clipUrl, 'Open App Clip')}</div>`;
  } else {
    // Desktop: the QR (shown above) lets a phone scan and continue there.
    actionHtml = '<p>Scan the QR code above with your phone to open the app.</p>';
  }

  const sidBlock = sessionId
    ? `<p class="sid">Session: <code>${escapeHtml(sessionId)}</code></p>`
    : '';
  const launchHtml =
    '<div id="launch">' +
    '<h1>Open in the app</h1>' +
    "<p>This Face Liveness QR Sample opens the mobile sample app. If it didn't open " +
    'automatically, use the option above for your device.</p>' +
    sidBlock +
    '</div>';

  return { qrHtml, actionHtml, guideHtml: flowGuideHtml(), launchHtml };
}

/** The `apple-itunes-app` smart-banner meta, shown only when an iOS appID is set. */
export function smartBannerMetadata(sessionId: string): Metadata {
  const iosAppId = process.env.IOS_APPLINK_APP_ID || process.env.IOS_APP_ID || '';
  const teamApp = iosAppId.includes('.') ? iosAppId.split('.').slice(1).join('.') : '';
  if (!teamApp || !sessionId) return {};
  return { other: { 'apple-itunes-app': `app-argument=${sessionId}` } };
}

interface GuideStep {
  num: string;
  api: string;
  who: string;
  client: string;
  server: string;
  verify: string;
}

/**
 * Static, step-by-step walkthrough of the complete attestation + liveness flow.
 * Each step is one HTTP call, described from three angles: Client / Server /
 * Verify. Mirrors the required client flow (a returning device calls verify
 * first, falling back to register on first run).
 */
function flowGuideHtml(): string {
  const steps: GuideStep[] = [
    {
      num: '0',
      api: 'GET / &rarr; POST Face /detectLiveness-sessions &rarr; /native',
      who: 'Web form / server',
      client:
        'On <code>/</code> you enter a Face resource + API key. The server starts a Face' +
        ' liveness session and redirects you to <code>/native/?s=&lt;sessionId&gt;</code>,' +
        ' which deep-links into the native sample app.',
      server:
        'Creates the Face session and keeps the session token + resource + key server-side' +
        ' (the browser never sees them); the app receives only a session id.',
      verify: '',
    },
    {
      num: '1',
      api: 'POST /api/attestation/challenge?s&amp;cid&amp;sys',
      who: 'App &rarr; backend',
      client:
        'Sends <code>s</code> (sessionId), <code>cid</code> (clientId) and <code>sys</code>' +
        ' (<code>ios</code> / <code>android</code>).',
      server:
        'Generates a random 32-byte nonce and stores <code>challengeHash = sha256(nonce)</code>' +
        ' against this session + clientId + platform, then returns it.',
      verify:
        'The challengeHash is a one-time anti-replay nonce that BOTH platforms must embed in' +
        ' the attested certificate, so the server can later prove the device key was minted' +
        ' for this exact session.',
    },
    {
      num: '2',
      api: 'POST /api/attestation/verify?s&amp;cid&amp;sys  <em>(returning device &mdash; tried FIRST)</em>',
      who: 'App &rarr; backend',
      client:
        'If the app already holds an attested cert + keypair on the device, it calls this' +
        ' <strong>first</strong>: it posts the existing <code>authPublicCert</code>, a signed' +
        ' <code>payload</code> and (iOS) a fresh App Attest <code>assertion</code>.',
      server:
        'Looks the cert up by SHA-256 thumbprint, confirms it belongs to this clientId +' +
        ' platform, (iOS) verifies the assertion and increments the signCount, then mints a fresh' +
        ' server EC key pair. Returns <code>{exists:true, serverEncryptionPublicKey}</code> when the' +
        ' cert is found (skip to step 4) or <code>{exists:false}</code> when it is not (fall through to step 3).',
      verify:
        'Re-binds an already-attested device without re-running full attestation &mdash; the App' +
        ' Attest / Play Integrity verdict captured at registration is reused.',
    },
    {
      num: '3',
      api: 'POST /api/attestation/register?s&amp;cid&amp;sys  <em>(first run, or fallback if verify returned exists:false)</em>',
      who: 'App &rarr; backend',
      client:
        'Creates two device keys &mdash; a hardware-backed <strong>auth key</strong> (used to' +
        ' <strong>sign</strong> every request) and an <strong>encryption key</strong> (the server' +
        ' encrypts the session token + acks to it). It posts the signing cert' +
        ' <code>authPublicCert</code>, a <code>signature</code>, and a signed <code>payload</code>' +
        ' = <code>{challengeHash, encryptionPublicCert, attestJson}</code>. Which key is actually' +
        ' attested differs by platform (Android attests the auth key itself; iOS attests a' +
        ' <em>separate</em> App Attest key and links it to the auth key via an assertion). The' +
        ' encryption cert is bound to this session by being' +
        ' carried inside that auth-key-signed payload next to the challengeHash.',
      server:
        'Verifies the ECDSA signature with the cert public key, checks the cert is valid +' +
        ' unexpired, runs full platform attestation (see Verify), then stores the cert by' +
        ' SHA-256 thumbprint and mints a server EC key pair (returns the server public key).',
      verify:
        '<strong>iOS App Attest:</strong> validate the x5c chain to Apple&rsquo;s App Attest root,' +
        ' then confirm the cert was <strong>minted for this session</strong> &mdash;' +
        ' <code>sha256(authData || challengeHashBytes)</code> must equal the credCert nonce' +
        ' extension (OID 1.2.840.113635.100.8.2); also confirm the credentialId binds the' +
        ' attested key and the chain is not on Apple&rsquo;s CRL.' +
        ' <strong>Android:</strong> validate the Key-Attestation chain to a pinned Google' +
        ' hardware root and confirm no certificate in the chain is on Google&rsquo;s attestation' +
        ' revocation status list, then confirm the cert was <strong>minted for this' +
        ' session</strong> &mdash; the challenge in the leaf cert&rsquo;s Key Attestation extension' +
        ' (OID 1.3.6.1.4.1.11129.2.1.17) must equal the challengeHash. <strong>Chain of' +
        ' trust:</strong> the Play Integrity' +
        ' token&rsquo;s <code>requestHash</code> must equal <code>sha256(leaf&nbsp;attestation&nbsp;cert)</code>,' +
        ' tying the integrity verdict to the very same attested key; then evaluate the app /' +
        ' device / account verdicts.',
    },
    {
      num: '4',
      api: 'POST /api/session/token?s',
      who: 'App &rarr; backend',
      client:
        'Encrypts <code>{challengeHash, clientId, system}</code> to the server public key,' +
        ' signs the ciphertext with the auth key, posts <code>encryptedData</code> +' +
        ' <code>signature</code> (+ iOS assertion).',
      server:
        'Verifies the signature (+ assertion), decrypts, confirms every field matches the' +
        ' session, marks auth complete, and returns the Face <em>session token</em> encrypted' +
        ' to the client encryption key.',
      verify:
        'Only the genuine attested device holds the private key needed to sign and to decrypt' +
        ' the response, so the Face token is released to that device alone.',
    },
    {
      num: '5',
      api: 'POST /api/liveness/digest?s',
      who: 'App &rarr; backend',
      client:
        'After the SDK runs the liveness check, the app computes a result digest, encrypts +' +
        ' signs it, and posts it (+ iOS assertion).',
      server:
        'Verifies signature / assertion, decrypts, checks clientId + OS match, stores the' +
        ' digest and marks the session <code>digestCompleted</code>; returns an encrypted ack.',
      verify:
        'Binds the liveness outcome to the same attested device. The stored client digest is a' +
        ' tamper-evident fingerprint of the liveness result: in step 6 the server MUST compare' +
        ' it against the digest the Face service reports for the session &mdash; the two must be' +
        ' identical. Any mismatch means the result was altered in transit and the session must' +
        ' be rejected.',
    },
    {
      num: '6',
      api: 'GET /api/session/result?s  <em>(this page polls it)</em>',
      who: 'Browser &rarr; backend',
      client: 'This page polls every 1 second with only the session id.',
      server:
        'Once <code>digestCompleted</code> is set, it queries the Face service with the' +
        ' server-held resource + key and returns the liveness decision.',
      verify:
        'Secrets stay on the server; the browser only ever receives the final decision. The' +
        ' server MUST also compare the client-supplied digest (step 5) against the digest in' +
        ' the Face service result &mdash; they have to match exactly, proving the result the' +
        ' device reported is the same one the service computed and was not tampered with.',
    },
  ];

  const items = steps
    .map((s) => {
      const verifyRow = s.verify
        ? `<p class="g-row"><span class="g-tag g-verify">Verify</span>${s.verify}</p>`
        : '';
      return (
        '<details class="g-step">' +
        `<summary><span class="g-num">${s.num}</span>` +
        `<span class="g-api">${s.api}</span>` +
        `<span class="g-who">${s.who}</span></summary>` +
        `<p class="g-row"><span class="g-tag g-client">Client</span>${s.client}</p>` +
        `<p class="g-row"><span class="g-tag g-server">Server</span>${s.server}</p>` +
        `${verifyRow}` +
        '</details>'
      );
    })
    .join('');

  const fraudNote =
    '<p class="g-note"><strong>iOS fraud-risk monitoring (recommended &mdash; not part of this' +
    ' sample).</strong> Beyond attestation, App Attest can also gauge fraud risk: your server' +
    ' sends the attestation <em>receipt</em> to Apple, which returns a Risk Metric &mdash; the' +
    ' approximate number of attested keys for that device over the last 30 days. An unusually' +
    ' high count can indicate a compromised device serving many app instances. This sample' +
    ' parses the receipt but does not call Apple; consider adding it. See ' +
    '<a href="https://developer.apple.com/documentation/devicecheck/assessing-fraud-risk"' +
    ' target="_blank" rel="noopener">Apple: Assessing fraud risk</a>.</p>';

  const recallNote =
    '<p class="g-note"><strong>Device recall / persistent device state (recommended &mdash; not' +
    ' part of this sample).</strong> Both platforms can store a few bits of state that stay with' +
    ' a physical device <em>across app reinstalls</em>, so your server can recognize a device' +
    ' that previously misbehaved even after the app (and its attested key) is wiped and' +
    ' re-registered. On Android, Play Integrity offers' +
    ' <a href="https://developer.android.com/google/play/integrity/device-recall"' +
    ' target="_blank" rel="noopener">device recall</a>; on iOS, DeviceCheck stores' +
    ' <a href="https://developer.apple.com/documentation/devicecheck/accessing-and-modifying-per-device-data"' +
    ' target="_blank" rel="noopener">two per-device bits</a> on an Apple server. Monitoring this' +
    ' is recommended to track device behavior and curb repeat abuse from the same hardware.</p>';

  const scopeWarning =
    '<p class="g-warn"><strong>Demo &amp; security notice.</strong> This sample only' +
    ' demonstrates secure communication between a mobile app and the backend. It validates the' +
    ' certificate attestation and app + device integrity (a genuine, unmodified app on real' +
    ' hardware) &mdash; but it does <strong>not</strong> link the attested certificate to a user' +
    ' account. In production you should strongly consider monitoring the' +
    ' certificate-to-user-account association, so the same phone (a single attested key) cannot' +
    ' be reused to onboard or back many different user accounts.</p>';

  return (
    '<details class="guide" open>' +
    '<summary class="guide-title">How the API flow works &mdash; step-by-step walkthrough</summary>' +
    '<p class="guide-intro">Each step below is a single HTTP call. The mobile app drives steps' +
    ' 1&ndash;5 to prove it is a genuine, unmodified app on real hardware (App Attest on iOS,' +
    ' Key Attestation + Play Integrity on Android) before the Face liveness token is released.' +
    ' A returning device that already has an attested cert calls <strong>verify</strong> first' +
    ' and only runs full <strong>register</strong> attestation on first use (or if the server no' +
    ' longer recognizes its cert). This browser page performs step 6 &mdash; polling for the final' +
    ' liveness result, shown above once the session completes.</p>' +
    scopeWarning +
    items +
    fraudNote +
    recallNote +
    '</details>'
  );
}
