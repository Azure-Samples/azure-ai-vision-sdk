'use client';

import { useEffect, useState } from 'react';
import type { LandingFragments } from '../_lib/session_landing';

/* ------------------------------------------------------------------ */
/* Result-rendering helpers (direct port of the session.py poll script) */
/* ------------------------------------------------------------------ */

function esc(s: unknown): string {
  return String(s).replace(
    /[&<>]/g,
    (c) => (({ '&': '&amp;', '<': '&lt;', '>': '&gt;' } as Record<string, string>)[c])
  );
}

function section(title: string, note: string, body: string): string {
  return (
    '<section class="section"><h3>' +
    esc(title) +
    '</h3>' +
    '<p class="section-note">' +
    esc(note) +
    '</p>' +
    '<pre>' +
    esc(body) +
    '</pre></section>'
  );
}

type SessionResult = {
  status?: string;
  result?: unknown;
  clientDigest?: string;
  message?: string;
  code?: string;
};

function digestHtml(d: SessionResult): string {
  if (!d.clientDigest) return '';
  return section(
    'Client digest',
    'The backend verified that this client digest matches the digest in the liveness service result below.',
    d.clientDigest
  );
}

function buildResultDone(d: SessionResult): string {
  return (
    '<h2>Session complete</h2>' +
    digestHtml(d) +
    section(
      'Liveness service result (JSON)',
      'This is the raw JSON the Face liveness service returned for the session.',
      JSON.stringify(d.result, null, 2)
    )
  );
}

/* ------------------------------------------------------------------ */
/* Component                                                           */
/* ------------------------------------------------------------------ */

export default function LivenessSession({
  sessionId,
  qrHtml,
  actionHtml,
  guideHtml,
  launchHtml,
}: LandingFragments & { sessionId: string }) {
  const [advOpen, setAdvOpen] = useState(false);
  const [resultHtml, setResultHtml] = useState('');

  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let tries = 0;
    const max = 600;

    setResultHtml('<p class="polling">Checking session\u2026</p>');

    const poll = () => {
      tries++;
      fetch('/api/session/result?s=' + encodeURIComponent(sessionId), { cache: 'no-store' })
        .then(async (response) => {
          const d: SessionResult = await response.json();
          if (cancelled) return;
          if (response.ok && d.status === 'done') {
            setResultHtml(buildResultDone(d));
            return;
          }
          if (response.ok && d.status === 'pending') {
            const tail =
              tries < max
                ? '<p class="polling">Waiting for the liveness result\u2026</p>'
                : '<p class="polling">Still waiting\u2026 the session may not be complete yet.</p>';
            setResultHtml(tail);
            if (tries < max) timer = setTimeout(poll, 1000);
            return;
          }
          const message = d.code === 'DIGEST_MISMATCH'
            ? 'Session rejected: the client and service digests are missing or do not match.'
            : d.status === 'notfound' ? 'Session not found or expired.'
            : d.message || `Could not load result (HTTP ${response.status}).`;
          setResultHtml('<p class="warn" role="alert">' + esc(message) + '</p>');
        })
        .catch(() => {
          if (cancelled) return;
          setResultHtml('<p class="warn" role="alert">Could not load the liveness result.' +
            (tries < max ? ' Retrying...' : '') + '</p>');
          if (tries < max) timer = setTimeout(poll, 1000);
        });
    };
    poll();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [sessionId]);

  return (
    <div className="session-page">
      <main className="card">
        <div className="panel">
          <div dangerouslySetInnerHTML={{ __html: qrHtml }} />
          <div dangerouslySetInnerHTML={{ __html: actionHtml }} />
          <div
            className="result"
            role="status"
            aria-live="polite"
            hidden={!resultHtml}
            dangerouslySetInnerHTML={{ __html: resultHtml }}
          />
        </div>
        <div className="adv">
          <button
            className="adv-toggle"
            type="button"
            aria-expanded={advOpen}
            onClick={() => setAdvOpen((o) => !o)}
          >
            {advOpen ? 'Advanced \u25B2' : 'Advanced \u25BC'}
          </button>
          <div className="adv-panel" hidden={!advOpen}>
            <div dangerouslySetInnerHTML={{ __html: launchHtml }} />
            <div dangerouslySetInnerHTML={{ __html: guideHtml }} />
          </div>
        </div>
      </main>
    </div>
  );
}
