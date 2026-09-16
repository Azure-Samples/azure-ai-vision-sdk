"""Server-side helpers for the deep-link landing page.

Builds the QR code, the platform-specific "open in app" action, and the static
step-by-step flow guide. Port of the React sample's ``session_landing.ts``.
"""

from __future__ import annotations

import io
import os
from urllib.parse import quote, urlencode, urlparse, urlunparse

import segno


def escape_html(s: str) -> str:
    return (
        str(s)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&#39;")
    )


def detect_platform(user_agent: str) -> str:
    """Classify the client as 'android', 'ios' or 'desktop' from its UA."""
    ua = (user_agent or "").lower()
    if "android" in ua:
        return "android"
    if any(t in ua for t in ("iphone", "ipad", "ipod")):
        return "ios"
    # iPadOS Safari masquerades as desktop macOS but reports a touch UA.
    if "macintosh" in ua and "mobile" in ua:
        return "ios"
    return "desktop"


def _append_query(url: str, params: dict[str, str]) -> str:
    extra = {k: v for k, v in params.items() if v}
    if not url or not extra:
        return url
    sep = "&" if "?" in url else "?"
    return f"{url}{sep}{urlencode(extra)}"


def _android_intent_url(https_url: str, pkg: str, fallback_url: str = "") -> str:
    """Build an Android ``intent://`` URI that force-opens the installed app.

    The scheme is pinned to https (the app's intent-filter is https-only and a
    TLS-terminating proxy can otherwise make the request look like http).
    """
    u = urlparse(https_url)
    target = u.netloc + u.path
    if u.query:
        target += "?" + u.query
    fragment = f"Intent;scheme=https;package={pkg};"
    if fallback_url:
        fragment += f"S.browser_fallback_url={quote(fallback_url, safe='')};"
    fragment += "end"
    return f"intent://{target}#{fragment}"


def _with_path(url: str, path: str, session_id: str = "") -> str:
    """Return ``url`` with its path swapped for ``path``, scheme pinned to https,
    and the query rebuilt with only ``s=<sessionId>``."""
    u = urlparse(url)
    query = f"s={quote(session_id, safe='')}" if session_id else ""
    return urlunparse(("https", u.netloc, path, "", query, ""))


def _qr_svg(data: str) -> str:
    """Render ``data`` as a self-contained inline SVG QR code (no external service)."""
    qr = segno.make(data, error="m")
    buff = io.BytesIO()
    qr.save(
        buff,
        kind="svg",
        border=2,
        dark="#0f1220",
        light="#ffffff",
        xmldecl=False,
        svgns=True,
        omitsize=True,
    )
    return buff.getvalue().decode("utf-8")


def _store_link(href: str, label: str, cls: str = "store") -> str:
    if not href:
        return ""
    return f'<a class="{cls}" href="{escape_html(href)}">{label}</a>'


def build_landing(session_id: str, page_url: str, user_agent: str) -> dict[str, str]:
    """Compute the landing-page HTML fragments: QR, platform action, launch info, guide."""
    ios_store = os.getenv("IOS_APP_STORE_URL", "")
    play_store = os.getenv("ANDROID_PLAY_STORE_URL", "")
    platform = detect_platform(user_agent)

    native_url = _with_path(page_url, "/native", session_id) if page_url else ""
    result_url = _with_path(page_url, "/result", session_id) if page_url else ""
    backend_host = urlparse(page_url).hostname or "" if page_url else ""
    qr_url = _append_query(native_url, {"callbackUrl": result_url}) if native_url else ""

    qr_html = (
        f'<div class="qr">{_qr_svg(qr_url)}<p class="qr-hint">'
        f"{escape_html('Scan with your phone to open the app.')}</p></div>"
        if qr_url
        else ""
    )

    if platform == "android":
        landing_url = _append_query(native_url, {"callbackUrl": result_url})
        pkg = (os.getenv("ANDROID_PACKAGE_NAME", "") or "").strip()
        if pkg and landing_url:
            play_fallback = _append_query(play_store, {"referrer": landing_url}) if play_store else landing_url
            open_url = _android_intent_url(landing_url, pkg, play_fallback)
        else:
            open_url = landing_url
        open_app = _store_link(open_url, "Open in app", "store open-app")
        action_html = (
            "<p>Open the app to run the check now. If it isn't installed yet, this takes you to Google Play.</p>"
            f"<div>{open_app}</div>"
        )
    elif platform == "ios":
        clip_url = _append_query(ios_store, {"s": session_id, "callbackUrl": result_url, "domain": backend_host})
        action_html = (
            "<p>Open the App Clip to run the check now, or get the full app from the App Store.</p>"
            f"<div>{_store_link(clip_url, 'Open App Clip')}</div>"
        )
    else:
        action_html = "<p>Scan the QR code above with your phone to open the app.</p>"

    sid_block = f'<p class="sid">Session: <code>{escape_html(session_id)}</code></p>' if session_id else ""
    launch_html = (
        '<div id="launch">'
        "<h1>Open in the app</h1>"
        "<p>This Face Liveness QR Sample opens the mobile sample app. If it didn't open "
        "automatically, use the option above for your device.</p>"
        f"{sid_block}"
        "</div>"
    )

    return {"qr_html": qr_html, "action_html": action_html, "guide_html": flow_guide_html(), "launch_html": launch_html}


def smart_banner_meta(session_id: str) -> str:
    """The ``apple-itunes-app`` smart-banner meta tag, when an iOS appID is set."""
    ios_app_id = os.getenv("IOS_APPLINK_APP_ID") or os.getenv("IOS_APP_ID") or ""
    team_app = ".".join(ios_app_id.split(".")[1:]) if "." in ios_app_id else ""
    if not team_app or not session_id:
        return ""
    return f'<meta name="apple-itunes-app" content="app-argument={escape_html(session_id)}">'


def flow_guide_html() -> str:
    """Static, step-by-step walkthrough of the attestation + liveness flow."""
    steps = [
        {
            "num": "0",
            "api": "GET / &rarr; POST Face /detectLiveness-sessions &rarr; /native",
            "who": "Web form / server",
            "client": "On <code>/</code> you enter a Face resource + API key. The server starts a Face"
            " liveness session and redirects you to <code>/native/?s=&lt;sessionId&gt;</code>,"
            " which deep-links into the native sample app.",
            "server": "Creates the Face session and keeps the session token + resource + key server-side"
            " (the browser never sees them); the app receives only a session id.",
            "verify": "",
        },
        {
            "num": "1",
            "api": "POST /api/attestation/challenge?s&amp;cid&amp;sys",
            "who": "App &rarr; backend",
            "client": "Sends <code>s</code> (sessionId), <code>cid</code> (clientId) and <code>sys</code>"
            " (<code>ios</code> / <code>android</code>).",
            "server": "Generates a random 32-byte nonce and stores <code>challengeHash = sha256(nonce)</code>"
            " against this session + clientId + platform, then returns it.",
            "verify": "The challengeHash is a one-time anti-replay nonce that BOTH platforms must embed in"
            " the attested certificate, so the server can later prove the device key was minted"
            " for this exact session.",
        },
        {
            "num": "2",
            "api": "POST /api/attestation/verify?s&amp;cid&amp;sys  <em>(returning device &mdash; tried FIRST)</em>",
            "who": "App &rarr; backend",
            "client": "If the app already holds an attested cert + keypair on the device, it calls this"
            " <strong>first</strong>: it posts the existing <code>authPublicCert</code>, a signed"
            " <code>payload</code> and (iOS) a fresh App Attest <code>assertion</code>.",
            "server": "Looks the cert up by SHA-256 thumbprint, confirms it belongs to this clientId +"
            " platform, (iOS) verifies the assertion and increments the signCount, then mints a fresh"
            " server EC key pair. Returns <code>{exists:true, serverEncryptionPublicKey}</code> when the"
            " cert is found (skip to step 4) or <code>{exists:false}</code> when it is not (fall through to step 3).",
            "verify": "Re-binds an already-attested device without re-running full attestation &mdash; the App"
            " Attest / Play Integrity verdict captured at registration is reused.",
        },
        {
            "num": "3",
            "api": "POST /api/attestation/register?s&amp;cid&amp;sys  <em>(first run, or fallback if verify returned exists:false)</em>",
            "who": "App &rarr; backend",
            "client": "Creates two device keys &mdash; a hardware-backed <strong>auth key</strong> (used to"
            " <strong>sign</strong> every request) and an <strong>encryption key</strong> (the server"
            " encrypts the session token + acks to it). It posts the signing cert"
            " <code>authPublicCert</code>, a <code>signature</code>, and a signed <code>payload</code>"
            " = <code>{challengeHash, encryptionPublicCert, attestJson}</code>. Which key is actually"
            " attested differs by platform (Android attests the auth key itself; iOS attests a"
            " <em>separate</em> App Attest key and links it to the auth key via an assertion). The"
            " encryption cert is bound to this session by being"
            " carried inside that auth-key-signed payload next to the challengeHash.",
            "server": "Verifies the ECDSA signature with the cert public key, checks the cert is valid +"
            " unexpired, runs full platform attestation (see Verify), then stores the cert by"
            " SHA-256 thumbprint and mints a server EC key pair (returns the server public key).",
            "verify": "<strong>iOS App Attest:</strong> validate the x5c chain to Apple&rsquo;s App Attest root,"
            " then confirm the cert was <strong>minted for this session</strong> &mdash;"
            " <code>sha256(authData || challengeHashBytes)</code> must equal the credCert nonce"
            " extension (OID 1.2.840.113635.100.8.2); also confirm the credentialId binds the"
            " attested key and the chain is not on Apple&rsquo;s CRL."
            " <strong>Android:</strong> validate the Key-Attestation chain to a pinned Google"
            " hardware root and confirm no certificate in the chain is on Google&rsquo;s attestation"
            " revocation status list, then confirm the cert was <strong>minted for this"
            " session</strong> &mdash; the challenge in the leaf cert&rsquo;s Key Attestation extension"
            " (OID 1.3.6.1.4.1.11129.2.1.17) must equal the challengeHash. <strong>Chain of"
            " trust:</strong> the Play Integrity"
            " token&rsquo;s <code>requestHash</code> must equal <code>sha256(leaf&nbsp;attestation&nbsp;cert)</code>,"
            " tying the integrity verdict to the very same attested key; then evaluate the app /"
            " device / account verdicts.",
        },
        {
            "num": "4",
            "api": "POST /api/session/token?s",
            "who": "App &rarr; backend",
            "client": "Encrypts <code>{challengeHash, clientId, system}</code> to the server public key,"
            " signs the ciphertext with the auth key, posts <code>encryptedData</code> +"
            " <code>signature</code> (+ iOS assertion).",
            "server": "Verifies the signature (+ assertion), decrypts, confirms every field matches the"
            " session, marks auth complete, and returns the Face <em>session token</em> encrypted"
            " to the client encryption key.",
            "verify": "Only the genuine attested device holds the private key needed to sign and to decrypt"
            " the response, so the Face token is released to that device alone.",
        },
        {
            "num": "5",
            "api": "POST /api/liveness/digest?s",
            "who": "App &rarr; backend",
            "client": "After the SDK runs the liveness check, the app computes a result digest, encrypts +"
            " signs it, and posts it (+ iOS assertion).",
            "server": "Verifies signature / assertion, decrypts, checks clientId + OS match, stores the"
            " digest and marks the session <code>digestCompleted</code>; returns an encrypted ack.",
            "verify": "Binds the liveness outcome to the same attested device. The stored client digest is a"
            " tamper-evident fingerprint of the liveness result: in step 6 the server MUST compare"
            " it against the digest the Face service reports for the session &mdash; the two must be"
            " identical. Any mismatch means the result was altered in transit and the session must"
            " be rejected.",
        },
        {
            "num": "6",
            "api": "GET /api/session/result?s  <em>(this page polls it)</em>",
            "who": "Browser &rarr; backend",
            "client": "This page polls every 1 second with only the session id.",
            "server": "Once <code>digestCompleted</code> is set, it queries the Face service with the"
            " server-held resource + key and returns the liveness decision.",
            "verify": "Secrets stay on the server; the browser only ever receives the final decision. The"
            " server MUST also compare the client-supplied digest (step 5) against the digest in"
            " the Face service result &mdash; they have to match exactly, proving the result the"
            " device reported is the same one the service computed and was not tampered with.",
        },
    ]

    def _item(s: dict[str, str]) -> str:
        verify_row = (
            f'<p class="g-row"><span class="g-tag g-verify">Verify</span>{s["verify"]}</p>'
            if s["verify"]
            else ""
        )
        return (
            '<details class="g-step">'
            f'<summary><span class="g-num">{s["num"]}</span>'
            f'<span class="g-api">{s["api"]}</span>'
            f'<span class="g-who">{s["who"]}</span></summary>'
            f'<p class="g-row"><span class="g-tag g-client">Client</span>{s["client"]}</p>'
            f'<p class="g-row"><span class="g-tag g-server">Server</span>{s["server"]}</p>'
            f"{verify_row}"
            "</details>"
        )

    items = "".join(_item(s) for s in steps)

    fraud_note = (
        '<p class="g-note"><strong>iOS fraud-risk monitoring (recommended &mdash; not part of this'
        " sample).</strong> Beyond attestation, App Attest can also gauge fraud risk: your server"
        " sends the attestation <em>receipt</em> to Apple, which returns a Risk Metric &mdash; the"
        " approximate number of attested keys for that device over the last 30 days. An unusually"
        " high count can indicate a compromised device serving many app instances. This sample"
        " parses the receipt but does not call Apple; consider adding it. See "
        '<a href="https://developer.apple.com/documentation/devicecheck/assessing-fraud-risk"'
        ' target="_blank" rel="noopener">Apple: Assessing fraud risk</a>.</p>'
    )

    recall_note = (
        '<p class="g-note"><strong>Device recall / persistent device state (recommended &mdash; not'
        " part of this sample).</strong> Both platforms can store a few bits of state that stay with"
        " a physical device <em>across app reinstalls</em>, so your server can recognize a device"
        " that previously misbehaved even after the app (and its attested key) is wiped and"
        " re-registered. On Android, Play Integrity offers"
        ' <a href="https://developer.android.com/google/play/integrity/device-recall"'
        ' target="_blank" rel="noopener">device recall</a>; on iOS, DeviceCheck stores'
        ' <a href="https://developer.apple.com/documentation/devicecheck/accessing-and-modifying-per-device-data"'
        ' target="_blank" rel="noopener">two per-device bits</a> on an Apple server. Monitoring this'
        " is recommended to track device behavior and curb repeat abuse from the same hardware.</p>"
    )

    scope_warning = (
        '<p class="g-warn"><strong>Demo &amp; security notice.</strong> This sample only'
        " demonstrates secure communication between a mobile app and the backend. It validates the"
        " certificate attestation and app + device integrity (a genuine, unmodified app on real"
        " hardware) &mdash; but it does <strong>not</strong> link the attested certificate to a user"
        " account. In production you should strongly consider monitoring the"
        " certificate-to-user-account association, so the same phone (a single attested key) cannot"
        " be reused to onboard or back many different user accounts.</p>"
    )

    return (
        '<details class="guide" open>'
        '<summary class="guide-title">How the API flow works &mdash; step-by-step walkthrough</summary>'
        '<p class="guide-intro">Each step below is a single HTTP call. The mobile app drives steps'
        " 1&ndash;5 to prove it is a genuine, unmodified app on real hardware (App Attest on iOS,"
        " Key Attestation + Play Integrity on Android) before the Face liveness token is released."
        " A returning device that already has an attested cert calls <strong>verify</strong> first"
        " and only runs full <strong>register</strong> attestation on first use (or if the server no"
        " longer recognizes its cert). This browser page performs step 6 &mdash; polling for the final"
        " liveness result, shown above once the session completes.</p>"
        f"{scope_warning}"
        f"{items}"
        f"{fraud_note}"
        f"{recall_note}"
        "</details>"
    )
