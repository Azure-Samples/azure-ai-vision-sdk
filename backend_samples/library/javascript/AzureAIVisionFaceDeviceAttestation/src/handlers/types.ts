/**
 * Framework-agnostic contract for the attestation route handlers.
 *
 * Each handler returns a typed {@link HandlerOutcome}: the SUGGESTED client
 * response (`status` + `body`) PLUS host-facing metadata — `ok`, a stable
 * machine-readable `code`, an internal `message`, and optional endpoint `data`.
 * The host (each Next.js `route.ts`) decides what to actually send: by default
 * it forwards `body`/`status`, but it can inspect `ok`/`code` to substitute a
 * message, hide 5xx internals, or act on `data` (e.g. the client digest). This
 * keeps the library from dictating client-facing policy and keeps handler logic
 * free of any HTTP-framework types so it can move into a library.
 *
 * Per-endpoint request/response types live next to their handler
 * (./challenge, ./register, ...); the shared shapes live here.
 */
import { trackApiFail } from '../api_telemetry';

/**
 * Typed result of every handler. Splits the CLIENT-facing suggestion (`status`
 * + `body`) from HOST-facing metadata (`ok`, `code`, `message`, `data`) the
 * caller uses to decide what to return and to observe what happened.
 */
export interface HandlerOutcome<TBody = unknown, TData = undefined> {
  /** true on success (2xx). Lets the host branch without parsing `status`. */
  ok: boolean;
  /** Stable machine-readable code: 'OK' on success, else the failure reason. */
  code: string;
  /** Suggested HTTP status; the host MAY override it. */
  status: number;
  /** Suggested client-facing JSON body (a success payload or {@link ErrorBody}). */
  body: TBody;
  /** Human-readable detail for host logging/decisions; not required to be shown. */
  message?: string;
  /** Endpoint-specific host-facing result (e.g. digest: `{ clientDigest }`). */
  data?: TData;
}

/**
 * Standard error response body. `expiredAt` / `validFrom` accompany the
 * certificate-time failures in the register/verify handlers.
 */
export interface ErrorBody {
  message: string;
  expiredAt?: string;
  validFrom?: string;
}

/**
 * Build a success (or otherwise non-failure) outcome. `status` defaults to 200
 * and `code` to 'OK' for any 2xx. Optional `data` carries endpoint-specific
 * host-facing output (e.g. the digest handler's client digest) alongside the
 * client-facing `body`.
 */
export function jsonResult<TBody, TData = undefined>(
  body: TBody,
  init?: { status?: number; code?: string; data?: TData }
): HandlerOutcome<TBody, TData> {
  const status = init?.status ?? 200;
  const okFlag = status < 400;
  const message =
    typeof body === 'object' && body !== null && 'message' in body
      ? String((body as { message?: unknown }).message)
      : undefined;
  return {
    ok: okFlag,
    code: init?.code ?? (okFlag ? 'OK' : 'ERROR'),
    status,
    body,
    message,
    data: init?.data,
  };
}

/**
 * Build a FAILURE outcome AND emit its failure telemetry in one step, so the
 * machine-readable `code` in the response and in telemetry can never drift.
 * `opts.properties` are the telemetry properties (sid, system, ...); `opts.body`
 * carries extra client-facing error fields (expiredAt / validFrom).
 */
export function fail(
  route: string,
  status: number,
  code: string,
  message: string,
  opts?: { properties?: Record<string, unknown>; body?: Omit<ErrorBody, 'message'> }
): HandlerOutcome<ErrorBody, never> {
  trackApiFail(route, code, status, opts?.properties);
  return {
    ok: false,
    code,
    status,
    message,
    body: { message, ...opts?.body },
  };
}
