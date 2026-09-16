"""Shared deep-link landing page for an existing liveness session.

Rendered by both `/native` (the App Link target) and `/result` (a plain path the
App Link filter never matches). Shows the QR / open-in-app action, polls
`/api/session/result` for the outcome, and exposes an Advanced drawer with the
launch info + flow guide. Port of the React `SessionPage` + `LivenessSession`.
"""

from __future__ import annotations

import json

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse

from ..attestation_service import get_attestation_service
from ..session_landing import build_landing, escape_html, smart_banner_meta

router = APIRouter()


def _host(request: Request) -> str:
    h = request.headers
    fwd = (h.get("x-forwarded-host") or "").split(",")[0].strip()
    return fwd or h.get("disguised-host") or h.get("host") or ""


def _error_card() -> str:
    return (
        '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        "<title>Session unavailable</title>"
        '<link rel="stylesheet" href="/static/styles.css"></head><body>'
        '<div class="session-page"><main class="card"><h1>Session unavailable</h1>'
        '<p class="error">This liveness session doesn\'t exist or has expired. Start a new session and try again.</p>'
        "</main></div></body></html>"
    )


_POLL_JS_BODY = r"""
var resultEl = document.getElementById('result');
function esc(s){ return String(s).replace(/[&<>]/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c]; }); }
function section(title, note, body){
  return '<section class="section"><h3>'+esc(title)+'</h3><p class="section-note">'+esc(note)+'</p><pre>'+esc(body)+'</pre></section>';
}
function digestHtml(d){
  if(!d.clientDigest) return '';
  return section('Client digest',
    'The backend verified that this client digest matches the digest in the liveness service result below.',
    d.clientDigest);
}
function buildResultDone(d){
  return '<h2>Session complete</h2>'+digestHtml(d)+section('Liveness service result (JSON)','This is the raw JSON the Face liveness service returned for the session.', JSON.stringify(d.result, null, 2));
}
var tries = 0, max = 600, timer;
resultEl.hidden = false;
resultEl.innerHTML = '<p class="polling">Checking session\u2026</p>';
function poll(){
  tries++;
  fetch('/api/session/result?s=' + encodeURIComponent(SESSION_ID), {cache: 'no-store'}).then(async function(response){
    var d = await response.json();
    if(response.ok && d.status === 'done'){ resultEl.innerHTML = buildResultDone(d); return; }
    if(response.ok && d.status === 'pending'){
      resultEl.innerHTML = tries < max ? '<p class="polling">Waiting for the liveness result\u2026</p>' : '<p class="polling">Still waiting\u2026 the session may not be complete yet.</p>';
      if(tries < max) timer = setTimeout(poll, 1000);
      return;
    }
    var message = d.code === 'DIGEST_MISMATCH'
      ? 'Session rejected: the client and service digests are missing or do not match.'
      : d.status === 'notfound' ? 'Session not found or expired.'
      : d.message || 'Could not load result (HTTP ' + response.status + ').';
    resultEl.innerHTML = '<p class="warn" role="alert">' + esc(message) + '</p>';
  }).catch(function(){
    resultEl.innerHTML = '<p class="warn" role="alert">Could not load the liveness result.' + (tries < max ? ' Retrying...' : '') + '</p>';
    if(tries < max) timer = setTimeout(poll, 1000);
  });
}
poll();
var advBtn = document.getElementById('adv-toggle');
var advPanel = document.getElementById('adv-panel');
var advOpen = false;
advBtn.addEventListener('click', function(){
  advOpen = !advOpen;
  advPanel.hidden = !advOpen;
  advBtn.setAttribute('aria-expanded', advOpen ? 'true' : 'false');
  advBtn.textContent = advOpen ? 'Advanced \u25B2' : 'Advanced \u25BC';
});
"""


def _render_session_page(session_id: str, landing: dict[str, str]) -> str:
    poll_js = "(function(){\nvar SESSION_ID = " + json.dumps(session_id) + ";\n" + _POLL_JS_BODY + "\n})();"
    return (
        '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        + smart_banner_meta(session_id)
        + "<title>Liveness session</title>"
        '<link rel="stylesheet" href="/static/styles.css"></head><body>'
        '<div class="session-page"><main class="card">'
        '<div class="panel">'
        f'<div>{landing["qr_html"]}</div>'
        f'<div>{landing["action_html"]}</div>'
        '<div class="result" id="result" role="status" aria-live="polite" hidden></div>'
        "</div>"
        '<div class="adv">'
        '<button class="adv-toggle" id="adv-toggle" type="button" aria-expanded="false">Advanced \u25bc</button>'
        '<div class="adv-panel" id="adv-panel" hidden>'
        f'<div>{landing["launch_html"]}</div>'
        f'<div>{landing["guide_html"]}</div>'
        "</div></div>"
        "</main></div>"
        f"<script>{poll_js}</script>"
        "</body></html>"
    )


async def _serve(request: Request, path: str) -> HTMLResponse:
    session_id = request.query_params.get("s", "")
    exists = await get_attestation_service().session_exists(session_id) if session_id else False
    if not exists:
        return HTMLResponse(_error_card())
    host = _host(request)
    page_url = f"https://{host}{path}?s={escape_html(session_id)}"
    landing = build_landing(session_id, page_url, request.headers.get("user-agent", ""))
    return HTMLResponse(_render_session_page(session_id, landing))


@router.get("/native", response_class=HTMLResponse)
@router.get("/native/", response_class=HTMLResponse)
async def native(request: Request):
    return await _serve(request, "/native")


@router.get("/result", response_class=HTMLResponse)
@router.get("/result/", response_class=HTMLResponse)
async def result(request: Request):
    return await _serve(request, "/result")
