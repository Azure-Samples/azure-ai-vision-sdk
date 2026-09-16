'use server';

import { redirect } from 'next/navigation';
import { createSession, FaceApiError } from './_lib/face_liveness_api';
import { getAttestationService } from './_lib/attestation_service';
import { saveAppSession } from './_lib/store';
import { trackApiFail } from './_lib/next_telemetry';

// Face resource names are restricted to letters, digits and hyphens. Validating
// strictly keeps the host pinned to *.cognitiveservices.azure.com (prevents SSRF
// via a crafted "resource" value).
const RESOURCE_RE = /^[A-Za-z0-9][A-Za-z0-9-]{1,62}$/;
// Supported liveness operation modes; the sample defaults to Passive-Active.
const VALID_MODES = ['Passive', 'PassiveActive'] as const;
const DEFAULT_MODE = 'PassiveActive';

export interface GenerateState {
  error?: string;
  resource?: string;
  mode?: string;
}

/**
 * Start a Face liveness session from a resource name + API key, store the
 * resulting session token server-side, and redirect to the launch page
 * (`/native/?s=<sid>`). Wired to the index form via `useActionState`.
 */
export async function generateSession(
  _prev: GenerateState,
  formData: FormData
): Promise<GenerateState> {
  const resource = String(formData.get('resource') ?? '').trim();
  const apiKey = String(formData.get('apiKey') ?? '').trim();
  const mode = String(formData.get('mode') ?? '').trim() || DEFAULT_MODE;

  if (!RESOURCE_RE.test(resource)) {
    trackApiFail('generate', 'INVALID_RESOURCE', 400);
    return { error: 'Invalid Face resource name.', resource, mode };
  }
  if (!apiKey) {
    trackApiFail('generate', 'MISSING_API_KEY', 400);
    return { error: 'API key is required.', resource, mode };
  }
  if (!VALID_MODES.includes(mode as (typeof VALID_MODES)[number])) {
    trackApiFail('generate', 'INVALID_MODE', 400);
    return { error: 'Invalid liveness operation mode.', resource, mode: DEFAULT_MODE };
  }

  // Optional verify image → run liveness with face verification.
  let verifyBytes: Uint8Array | null = null;
  let verifyName = 'verify.jpg';
  const verifyImage = formData.get('verifyImage');
  if (verifyImage && typeof verifyImage === 'object' && 'arrayBuffer' in verifyImage) {
    const file = verifyImage as File;
    if (file.size > 0 && file.name) {
      verifyBytes = new Uint8Array(await file.arrayBuffer());
      verifyName = file.name;
    }
  }
  const action = verifyBytes ? 'detectLivenessWithVerify' : 'detectLiveness';

  let session;
  try {
    session = await createSession(resource, apiKey, mode, verifyBytes, verifyName);
  } catch (err) {
    if (err instanceof FaceApiError) {
      trackApiFail('generate', 'SESSION_CREATE_FAIL', err.status, { upstreamStatus: err.status });
      return {
        error: `Failed to start session (HTTP ${err.status}). Check the resource and key.`,
        resource,
        mode,
      };
    }
    trackApiFail('generate', 'SESSION_CREATE_ERROR', 502);
    return { error: 'Could not reach the Face service. Check the resource name.', resource, mode };
  }

  const sessionId = session.sessionId;
  const authToken = session.authToken;
  if (!sessionId || !authToken) {
    trackApiFail('generate', 'SESSION_RESPONSE_INCOMPLETE', 502);
    return { error: 'Face service returned an unexpected response.', resource, mode };
  }

  // Seed the attestation library's session with the Face token; the library
  // releases it to the device only after attestation passes.
  const stored = await getAttestationService().saveSession(sessionId, authToken);
  if (!stored) {
    trackApiFail('generate', 'SAVE_TOKEN_FAIL', 500, { sid: sessionId });
    return { error: 'Could not store the session token. Try again.', resource, mode };
  }

  // resource/apiKey/action go in the app's OWN session store (session/<sid>),
  // separate from the library's record, so /api/session/result can poll the
  // Face service later. Never returned to the browser.
  const appStored = await saveAppSession(sessionId, { resource, apiKey, action });
  if (!appStored) {
    trackApiFail('generate', 'SAVE_APP_SESSION_FAIL', 500, { sid: sessionId });
    return { error: 'Could not store the session. Try again.', resource, mode };
  }

  // Success: hand off to the launch page. `redirect` throws internally, so it
  // must stay outside the try/catch above.
  redirect(`/native/?s=${sessionId}`);
}
