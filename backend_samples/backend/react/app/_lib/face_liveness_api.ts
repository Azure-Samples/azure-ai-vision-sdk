/**
 * Face liveness session token requests.
 *
 * Thin wrapper around the Azure Face liveness-session REST endpoints used by the
 * landing / token-generation pages. Keeping the HTTP calls here lets the web
 * layer stay focused on request handling and rendering.
 *
 * Face resource names are restricted to letters, digits and hyphens, so the
 * host built below stays pinned to `*.cognitiveservices.azure.com` (prevents
 * SSRF via a crafted "resource" value); callers should still validate the
 * resource before calling in.
 */

const FACE_API_VERSION = process.env.FACE_API_VERSION || 'v1.2';

export interface CreateSessionResult {
  sessionId?: string;
  authToken?: string;
  [key: string]: unknown;
}

export interface FaceRectangle {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** Per-modality face box; only `color` is populated for the current sessions. */
export interface LivenessTargets {
  color?: {
    faceRectangle?: FaceRectangle;
  };
}

/** Face-verification outcome, present only for detectLivenessWithVerify sessions. */
export interface VerifyResult {
  matchConfidence?: number;
  isIdentical?: boolean;
}

/** Per-attempt success payload (present when attemptStatus === 'Succeeded'). */
export interface LivenessAttemptResult {
  livenessDecision?: string;
  targets?: LivenessTargets;
  verifyResult?: VerifyResult;
  digest?: string;
  sessionImageId?: string;
  verifyImageHash?: string;
}

/** Per-attempt failure payload (present when attemptStatus === 'Failed'). */
export interface LivenessAttemptError {
  code?: string;
  message?: string;
  targets?: LivenessTargets;
}

export interface LivenessAttempt {
  attemptId?: number;
  attemptStatus?: string;
  result?: LivenessAttemptResult;
  error?: LivenessAttemptError;
}

/** A reference face supplied for verification (detectLivenessWithVerify). */
export interface VerifyReference {
  referenceType?: string;
  faceRectangle?: FaceRectangle;
  qualityForRecognition?: string;
}

/** Parsed response from a (detectLiveness / detectLivenessWithVerify) session query. */
export interface LivenessSessionResult {
  sessionId?: string;
  authToken?: string;
  status?: string;
  modelVersion?: string;
  results?: {
    attempts?: LivenessAttempt[];
    verifyReferences?: VerifyReference[];
  };
  [key: string]: unknown;
}

/** Error thrown for a non-2xx response from the Face service. */
export class FaceApiError extends Error {
  status: number;
  body: string;
  constructor(message: string, status: number, body = '') {
    super(message);
    this.name = 'FaceApiError';
    this.status = status;
    this.body = body;
  }
}

/**
 * Start a Face liveness session and return the parsed response.
 *
 * When `verifyImage` is supplied the session is created as a
 * detectLivenessWithVerify session (multipart upload of the reference photo);
 * otherwise a plain detectLiveness session. Throws {@link FaceApiError} on a
 * non-2xx response.
 */
export async function createSession(
  resource: string,
  apiKey: string,
  mode: string,
  verifyImage?: Uint8Array | null,
  verifyImageName = 'verify.jpg'
): Promise<CreateSessionResult> {
  const action = verifyImage ? 'detectLivenessWithVerify' : 'detectLiveness';
  const endpoint =
    `https://${resource}.cognitiveservices.azure.com` +
    `/face/${FACE_API_VERSION}/${action}-sessions`;

  let resp: Response;
  if (verifyImage) {
    // Create-with-verify: send the image part + fields as multipart and let
    // fetch set the Content-Type/boundary automatically.
    const form = new FormData();
    form.append('livenessOperationMode', mode);
    form.append('enableSessionImage', 'true');
    form.append('deviceCorrelationIdSetInClient', 'true');
    form.append('deviceCorrelationIdSetInSessionStart', 'true');
    form.append(
      'verifyImage',
      // Cast: a Uint8Array is a valid BlobPart at runtime; the DOM lib's
      // ArrayBufferView<ArrayBuffer> generic is stricter than Node's typings.
      new Blob([verifyImage as unknown as BlobPart], { type: 'application/octet-stream' }),
      verifyImageName
    );
    resp = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Ocp-Apim-Subscription-Key': apiKey },
      body: form,
    });
  } else {
    resp = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Ocp-Apim-Subscription-Key': apiKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        livenessOperationMode: mode,
        enableSessionImage: true,
        deviceCorrelationIdSetInClient: true,
        deviceCorrelationIdSetInSessionStart: true,
      }),
    });
  }

  if (!resp.ok) {
    const text = await resp.text().catch(() => '');
    throw new FaceApiError(`createSession failed: HTTP ${resp.status}`, resp.status, text);
  }
  return (await resp.json()) as CreateSessionResult;
}

/**
 * Fetch the liveness session result from the Face service. Throws
 * {@link FaceApiError} on a non-2xx response.
 */
export async function querySessionResult(
  resource: string,
  apiKey: string,
  action: string,
  sessionId: string
): Promise<LivenessSessionResult> {
  const endpoint =
    `https://${resource}.cognitiveservices.azure.com` +
    `/face/${FACE_API_VERSION}/${action}-sessions/${sessionId}`;
  const resp = await fetch(endpoint, {
    method: 'GET',
    headers: { 'Ocp-Apim-Subscription-Key': apiKey },
  });
  if (!resp.ok) {
    const text = await resp.text().catch(() => '');
    throw new FaceApiError(`querySessionResult failed: HTTP ${resp.status}`, resp.status, text);
  }
  return (await resp.json()) as LivenessSessionResult;
}
