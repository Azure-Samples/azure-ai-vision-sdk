/**
 * Next.js App Router telemetry adapter.
 *
 * `withApiTelemetry(route, handler)` wraps a route handler to emit request
 * telemetry (populates the AI `requests` table) plus exception tracking. This
 * is the only Next-coupled piece of the attestation request pipeline — the
 * handlers themselves are framework-agnostic inside
 * `@azure/ai-vision-face-deviceattestation`, and the failure-event helper
 * (`trackApiFail`) lives framework-free in that same package.
 */
import { NextRequest, NextResponse } from 'next/server';
import { trackEvent, trackException, trackRequest } from './app_insights_server';

/**
 * Emit a structured `Api.Fail` event (route + reason + status) for app-level
 * server actions/handlers. Mirrors the attestation library's internal helper
 * but lives host-side so app code never reaches into the attestation package.
 */
export function trackApiFail(
  route: string,
  reason: string,
  status: number,
  properties?: Record<string, unknown>
): void {
  trackEvent('Api.Fail', { route, reason, status, ...properties });
}

export function withApiTelemetry(
  route: string,
  handler: (req: NextRequest) => Promise<NextResponse>
): (req: NextRequest) => Promise<NextResponse> {
  return async (req: NextRequest) => {
    const startTime = Date.now();
    const method = req.method || 'POST';
    const url = `${req.nextUrl.pathname}${req.nextUrl.search}`;

    trackEvent('Api.Start', { route, method, url });

    let response: NextResponse;
    try {
      response = await handler(req);
    } catch (err) {
      const duration = Date.now() - startTime;
      trackException(err, { source: `Api.${route}`, url });
      trackRequest({
        name: `${method} /api/${route}`,
        url,
        duration,
        resultCode: 500,
        success: false,
        properties: { route, method, unhandledException: true },
      });
      trackEvent(
        'Api.Fail',
        {
          route,
          reason: 'UNHANDLED_EXCEPTION',
          status: 500,
          errorMessage: err instanceof Error ? err.message : String(err),
        },
        { durationMs: duration }
      );
      return NextResponse.json({ message: 'Internal server error' }, { status: 500 });
    }

    const duration = Date.now() - startTime;
    const status = response.status;
    const success = status < 400;

    trackRequest({
      name: `${method} /api/${route}`,
      url,
      duration,
      resultCode: status,
      success,
      properties: { route, method },
    });

    trackEvent(
      success ? 'Api.Success' : 'Api.HttpFail',
      { route, method, status },
      { durationMs: duration }
    );

    return response;
  };
}
