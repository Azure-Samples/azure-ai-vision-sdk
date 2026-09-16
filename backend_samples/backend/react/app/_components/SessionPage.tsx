import { headers } from 'next/headers';
import { getAttestationService } from '../_lib/attestation_service';
import { buildLanding } from '../_lib/session_landing';
import LivenessSession from './LivenessSession';

function ErrorCard({ message }: { message: string }) {
  return (
    <div className="session-page">
      <main className="card">
        <h1>Session unavailable</h1>
        <p className="error">{message}</p>
      </main>
    </div>
  );
}

/**
 * Shared landing page for an existing liveness session. Rendered by both
 * `/native` (the App Link target) and `/result` (a plain path the App Link
 * filter never matches). Unknown or expired sessions get an error card.
 */
export default async function SessionPage({
  sessionId,
  path,
}: {
  sessionId: string;
  path: string;
}) {
  const exists = sessionId ? await getAttestationService().sessionExists(sessionId) : false;
  if (!exists) {
    return (
      <ErrorCard message="This liveness session doesn't exist or has expired. Start a new session and try again." />
    );
  }

  const h = await headers();
  // Behind the App Service reverse proxy the raw `Host` header is the internal
  // origin (localhost:<port>), so the public domain must be read from the
  // forwarded headers it sets (X-Forwarded-Host / DISGUISED-HOST). Fall back to
  // `Host` for local dev.
  const host =
    (h.get('x-forwarded-host') || '').split(',')[0].trim() ||
    h.get('disguised-host') ||
    h.get('host') ||
    '';
  const userAgent = h.get('user-agent') || '';
  // https is forced (see withPath): App Links are https-only and a
  // TLS-terminating proxy can otherwise make the request look like http.
  const pageUrl = `https://${host}${path}?s=${encodeURIComponent(sessionId)}`;

  const landing = await buildLanding(sessionId, pageUrl, userAgent);
  return <LivenessSession sessionId={sessionId} {...landing} />;
}
