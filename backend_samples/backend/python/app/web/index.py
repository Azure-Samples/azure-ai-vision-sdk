"""Index page: the token-generation form + a deep-link/attestation config table.

`GET /` renders the form; `POST /` starts a Face liveness session from the
submitted resource + API key, seeds the attestation session, stores the app's
polling context, and redirects to `/native/?s=<sid>`. Port of the React sample's
`page.tsx` + `GenerateForm.tsx` + `actions.ts`.
"""

from __future__ import annotations

import os
import re
from typing import Optional

from fastapi import APIRouter, File, Form, Request, UploadFile
from fastapi.responses import HTMLResponse, RedirectResponse

from ..attestation_service import get_attestation_service
from ..face_liveness_api import FaceApiError, create_session
from ..request_limits import MAX_VERIFY_IMAGE_BYTES
from ..session_landing import escape_html
from ..store.app_session_store import AppSession, save_app_session
from ..telemetry.fastapi_telemetry import track_api_fail

router = APIRouter()

# Face resource names are letters/digits/hyphens only -> host stays pinned to
# *.cognitiveservices.azure.com (prevents SSRF via a crafted "resource").
_RESOURCE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9-]{1,62}$")
_VALID_MODES = ("Passive", "PassiveActive")
_DEFAULT_MODE = "PassiveActive"

# (name, purpose, docUrl) — only PRESENCE is shown on the page, never the value.
_REQUIRED_VARS = [
    ("IOS_APP_ID", "iOS App Attest: TeamID.BundleID the App Attest attestation/assertion must match.", "https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server"),
    ("IOS_APP_CLIP_ID", "iOS App Clip appID written into the AASA appclips section.", "https://developer.apple.com/documentation/xcode/supporting-associated-domains"),
    ("IOS_APPLINK_APP_ID", "Universal Link AASA appIDs binding the domain to the iOS app (falls back to IOS_APP_ID).", "https://developer.apple.com/documentation/xcode/supporting-associated-domains"),
    ("ANDROID_PACKAGE_NAME", "Android applicationId used for Play Integrity checks and the assetlinks binding.", "https://developer.android.com/google/play/integrity/overview"),
    ("ANDROID_SHA256_CERT_FINGERPRINTS", "App Link assetlinks signing-cert SHA-256 fingerprints.", "https://developers.google.com/digital-asset-links/v1/getting-started"),
    ("GOOGLE_SERVICE_ACCOUNT_JSON", "Credentials used to call the Play Integrity API (secret \u2014 value never shown).", "https://developer.android.com/google/play/integrity/setup"),
    ("APPLINK_PATH", "Universal/App Link path pattern (defaults to /native* when unset).", "https://developer.apple.com/documentation/xcode/supporting-associated-domains"),
]
_OPTIONAL_VARS = [
    ("DEBUG_MODE", 'Relaxes dev attestation policy: allows the App Attest "appattestdevelop" aaguid and Android UNRECOGNIZED_VERSION / weaker integrity.', "https://developer.android.com/google/play/integrity/verdicts"),
    ("ALLOW_DEVICE_INTEGRITY", "Accept Play Integrity MEETS_DEVICE_INTEGRITY (not only STRONG) as a pass.", "https://developer.android.com/google/play/integrity/verdicts"),
    ("ALLOW_BASIC_INTEGRITY", "Accept Play Integrity MEETS_BASIC_INTEGRITY (not only STRONG) as a pass.", "https://developer.android.com/google/play/integrity/verdicts"),
    ("ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE", "Accept Android attestation on hardware Key Attestation alone when the Play Integrity API is unavailable / quota-exceeded.", "https://developer.android.com/google/play/integrity/verdicts"),
    ("IOS_APP_STORE_URL", "App Store link on the landing page; also the iOS App Clip URL (receives ?s=<sessionId>).", ""),
    ("ANDROID_PLAY_STORE_URL", "Google Play link shown on the landing page.", ""),
]


def _config_rows(vars_list, required_group: bool) -> str:
    rows = []
    for name, purpose, doc_url in vars_list:
        is_set = bool((os.getenv(name) or "").strip())
        pill = "ok" if is_set else ("miss" if required_group else "muted")
        label = "Set" if is_set else "Not set"
        doc = (
            f' <a class="cfg-doc" href="{escape_html(doc_url)}" target="_blank" rel="noopener noreferrer">Docs &#8599;</a>'
            if doc_url
            else ""
        )
        rows.append(
            f'<tr><td class="cfg-name">{escape_html(name)}</td>'
            f'<td><span class="pill {pill}">{label}</span></td>'
            f'<td class="cfg-use">{escape_html(purpose)}{doc}</td></tr>'
        )
    return "".join(rows)


def _config_html() -> str:
    set_count = sum(1 for n, _, _ in _REQUIRED_VARS if (os.getenv(n) or "").strip())
    total = len(_REQUIRED_VARS)
    missing = [n for n, _, _ in _REQUIRED_VARS if not (os.getenv(n) or "").strip()]
    missing_note = (
        "Not configured (required): " + ", ".join(missing)
        if missing
        else "All required deep-link / attestation variables are configured."
    )
    open_attr = " open" if missing else ""
    return (
        f'<details class="config"{open_attr}>'
        f"<summary>Deep-link &amp; attestation config &mdash; {set_count}/{total} required set</summary>"
        f'<p class="hint">{escape_html(missing_note)}</p>'
        '<table class="cfg"><tbody>'
        '<tr class="cfg-group"><td colspan="3">Required</td></tr>'
        f"{_config_rows(_REQUIRED_VARS, True)}"
        '<tr class="cfg-group"><td colspan="3">Optional</td></tr>'
        f"{_config_rows(_OPTIONAL_VARS, False)}"
        "</tbody></table>"
        '<p class="hint">Only whether each variable is set is shown &mdash; values (including the'
        " service-account JSON) are never exposed.</p>"
        "</details>"
    )


def _render_index(error: Optional[str] = None, resource: str = "", mode: str = _DEFAULT_MODE) -> str:
    error_html = f'<p class="error">{escape_html(error)}</p>' if error else ""
    passive_active_sel = " selected" if mode != "Passive" else ""
    passive_sel = " selected" if mode == "Passive" else ""
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Start a Liveness Session</title>
  <link rel="stylesheet" href="/static/styles.css">
</head>
<body>
  <div class="index-page">
    <main class="card">
      <h1>Start a Liveness Session</h1>
      <p class="help">Enter your Azure Face resource and API key to generate a session token. You'll be
        redirected into the liveness launch page.</p>
      {error_html}
      <form method="post" action="/" enctype="multipart/form-data">
        <label for="resource">Face resource name</label>
        <div class="url-field">
          <span class="affix">https://</span>
          <input id="resource" name="resource" required autocomplete="off" placeholder="my-face-resource" value="{escape_html(resource)}">
          <span class="affix">.cognitiveservices.azure.com</span>
        </div>

        <label for="apiKey">API key</label>
        <input id="apiKey" name="apiKey" type="password" required autocomplete="off" placeholder="Ocp-Apim-Subscription-Key">

        <label for="mode">Liveness operation mode</label>
        <select id="mode" name="mode">
          <option value="PassiveActive"{passive_active_sel}>Passive-Active (default)</option>
          <option value="Passive"{passive_sel}>Passive</option>
        </select>

        <label for="verifyImage">Verify image (optional)</label>
        <input id="verifyImage" name="verifyImage" type="file" accept="image/*">
        <p class="hint">Attach a reference photo to run liveness <em>with face verification</em>. Leave empty for
          liveness only.</p>

        <button type="submit">Generate &amp; launch</button>
      </form>
      {_config_html()}
    </main>
  </div>
</body>
</html>"""


@router.get("/", response_class=HTMLResponse)
async def index():
    return HTMLResponse(_render_index())


@router.post("/")
async def generate(
    resource: str = Form(""),
    apiKey: str = Form(""),
    mode: str = Form(_DEFAULT_MODE),
    verifyImage: Optional[UploadFile] = File(None),
):
    resource = (resource or "").strip()
    api_key = (apiKey or "").strip()
    mode = (mode or "").strip() or _DEFAULT_MODE

    if not _RESOURCE_RE.match(resource):
        track_api_fail("generate", "INVALID_RESOURCE", 400)
        return HTMLResponse(_render_index("Invalid Face resource name.", resource, mode), status_code=400)
    if not api_key:
        track_api_fail("generate", "MISSING_API_KEY", 400)
        return HTMLResponse(_render_index("API key is required.", resource, mode), status_code=400)
    if mode not in _VALID_MODES:
        track_api_fail("generate", "INVALID_MODE", 400)
        return HTMLResponse(_render_index("Invalid liveness operation mode.", resource, _DEFAULT_MODE), status_code=400)

    verify_bytes = None
    verify_name = "verify.jpg"
    if verifyImage is not None and verifyImage.filename:
        data = await verifyImage.read(MAX_VERIFY_IMAGE_BYTES + 1)
        if len(data) > MAX_VERIFY_IMAGE_BYTES:
            track_api_fail("generate", "VERIFY_IMAGE_TOO_LARGE", 413)
            return HTMLResponse(
                _render_index("Reference image must be 6 MiB or smaller.", resource, mode),
                status_code=413,
            )
        if data:
            verify_bytes = data
            verify_name = verifyImage.filename
    action = "detectLivenessWithVerify" if verify_bytes else "detectLiveness"

    try:
        session = await create_session(resource, api_key, mode, verify_bytes, verify_name)
    except FaceApiError as err:
        track_api_fail("generate", "SESSION_CREATE_FAIL", err.status, {"upstreamStatus": err.status})
        return HTMLResponse(
            _render_index(f"Failed to start session (HTTP {err.status}). Check the resource and key.", resource, mode),
            status_code=400,
        )
    except Exception:  # noqa: BLE001
        track_api_fail("generate", "SESSION_CREATE_ERROR", 502)
        return HTMLResponse(
            _render_index("Could not reach the Face service. Check the resource name.", resource, mode),
            status_code=502,
        )

    session_id = session.get("sessionId")
    auth_token = session.get("authToken")
    if not session_id or not auth_token:
        track_api_fail("generate", "SESSION_RESPONSE_INCOMPLETE", 502)
        return HTMLResponse(_render_index("Face service returned an unexpected response.", resource, mode), status_code=502)

    stored = await get_attestation_service().save_session(session_id, auth_token)
    if not stored:
        track_api_fail("generate", "SAVE_TOKEN_FAIL", 500, {"sid": session_id})
        return HTMLResponse(_render_index("Could not store the session token. Try again.", resource, mode), status_code=500)

    app_stored = await save_app_session(session_id, AppSession(resource=resource, api_key=api_key, action=action))
    if not app_stored:
        track_api_fail("generate", "SAVE_APP_SESSION_FAIL", 500, {"sid": session_id})
        return HTMLResponse(_render_index("Could not store the session. Try again.", resource, mode), status_code=500)

    return RedirectResponse(url=f"/native/?s={session_id}", status_code=303)
