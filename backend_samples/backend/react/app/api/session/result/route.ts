import { NextRequest, NextResponse } from 'next/server';
import { getAttestationService } from '../../../_lib/attestation_service';
import { getAppSession } from '../../../_lib/store';
import {
  querySessionResult,
  FaceApiError,
  type LivenessSessionResult,
} from '../../../_lib/face_liveness_api';

// Poll the Face service for a session's liveness (and verify) outcome. The
// resource + API key live in the app's OWN session store (session/<sid>), kept
// separate from the attestation library's record, so the browser never sees
// those credentials and the library never sees the app's polling context.

export async function GET(request: NextRequest) {
  const sessionId = request.nextUrl.searchParams.get('s') || '';
  if (!sessionId) {
    return NextResponse.json({ status: 'error', message: 'Missing session ID' }, { status: 400 });
  }

  const appSession = await getAppSession(sessionId);
  if (!appSession) {
    return NextResponse.json({ status: 'notfound' }, { status: 404 });
  }

  const { resource, apiKey } = appSession;
  const action = appSession.action || 'detectLiveness';
  if (!resource || !apiKey) {
    return NextResponse.json(
      { status: 'error', message: 'Session has no query credentials' },
      { status: 409 }
    );
  }

  // Ask the attestation library whether the client has submitted its liveness
  // digest yet (and, if so, what it was) — via a typed method, without touching
  // the library's internal session-record shape.
  const liveness = await getAttestationService().getLivenessOutcome(sessionId);
  if (!liveness.completed) {
    return NextResponse.json({ status: 'pending' });
  }

  let result: LivenessSessionResult;
  try {
    result = await querySessionResult(resource, apiKey, action, sessionId);
  } catch (err) {
    if (err instanceof FaceApiError) {
      return NextResponse.json(
        { status: 'error', message: `Result query failed (HTTP ${err.status})` },
        { status: 502 }
      );
    }
    return NextResponse.json(
      { status: 'error', message: 'Could not reach the Face service' },
      { status: 502 }
    );
  }

  const attempts = (result.results && result.results.attempts) || [];
  const attempt = (attempts[0] && attempts[0].result) || {};
  if (!attempt.livenessDecision) {
    return NextResponse.json({ status: 'pending' });
  }
  if (typeof liveness.clientDigest !== 'string' || !liveness.clientDigest.trim()
      || typeof attempt.digest !== 'string' || liveness.clientDigest !== attempt.digest) {
    return NextResponse.json(
      { status: 'error', code: 'DIGEST_MISMATCH', message: 'Liveness result digest validation failed' },
      { status: 409 }
    );
  }

  return NextResponse.json({
    status: 'done',
    result,
    clientDigest: liveness.clientDigest,
  });
}
