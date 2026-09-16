/**
 * Framework-agnostic per-route failure telemetry.
 *
 * `trackApiFail(route, reason, status, props?)` emits a structured customEvent
 * so failure reasons can be grouped/queried by route + reason code. Handlers
 * call it directly; it has no HTTP-framework dependency.
 *
 * The Next.js request-telemetry wrapper (`withApiTelemetry`) lives app-side in
 * app/_lib/next_telemetry.ts.
 */
import { trackEvent } from './logging';

export function trackApiFail(
  route: string,
  reason: string,
  status: number,
  properties?: Record<string, unknown>
): void {
  trackEvent('Api.Fail', {
    route,
    reason,
    status,
    ...properties,
  });
}
