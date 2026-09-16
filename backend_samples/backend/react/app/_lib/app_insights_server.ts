/**
 * Server-side Application Insights wrapper.
 *
 * Uses the Node SDK ('applicationinsights') — the existing `components/appinsights.tsx`
 * is the browser SDK and does not emit telemetry from server code (API routes,
 * server components).
 *
 * Configure via APPLICATIONINSIGHTS_CONNECTION_STRING (preferred). For backwards
 * compatibility the NEXT_PUBLIC_APPINSIGHTS_INSTRUMENTATION_KEY env var is also
 * accepted. The attestation service uses a console logger when neither is set.
 */

import * as appInsights from 'applicationinsights';
import type { TelemetryClient } from 'applicationinsights';

let initAttempted = false;
let client: TelemetryClient | null = null;

function initIfNeeded(): TelemetryClient | null {
  if (initAttempted) return client;
  initAttempted = true;

  const connectionString =
    process.env.APPLICATIONINSIGHTS_CONNECTION_STRING ||
    (process.env.NEXT_PUBLIC_APPINSIGHTS_INSTRUMENTATION_KEY
      ? `InstrumentationKey=${process.env.NEXT_PUBLIC_APPINSIGHTS_INSTRUMENTATION_KEY}`
      : '');

  if (!connectionString) {
    console.warn(
      'App Insights server SDK not configured: APPLICATIONINSIGHTS_CONNECTION_STRING and NEXT_PUBLIC_APPINSIGHTS_INSTRUMENTATION_KEY are both unset.'
    );
    return null;
  }

  try {
    appInsights
      .setup(connectionString)
      .setAutoCollectConsole(true, true)
      .setAutoCollectExceptions(true)
      .setAutoCollectRequests(true)
      .setAutoCollectDependencies(true)
      .setAutoCollectPerformance(true, true)
      .setSendLiveMetrics(false)
      .start();
    client = appInsights.defaultClient;
    if (client) {
      client.context.tags[client.context.keys.cloudRole] =
        process.env.WEBSITE_SITE_NAME || 'liveness-webapp';
    }
  } catch (err) {
    console.error('App Insights init failed:', err);
    client = null;
  }
  return client;
}

type Props = Record<string, unknown> | undefined;

function stringifyProps(props: Props): Record<string, string> | undefined {
  if (!props) return undefined;
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(props)) {
    if (v === undefined || v === null) continue;
    if (typeof v === 'string') {
      out[k] = v;
    } else if (typeof v === 'number' || typeof v === 'boolean') {
      out[k] = String(v);
    } else {
      try {
        out[k] = JSON.stringify(v);
      } catch {
        out[k] = String(v);
      }
    }
  }
  return out;
}

function numericMeasurements(
  measurements?: Record<string, number>
): Record<string, number> | undefined {
  if (!measurements) return undefined;
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(measurements)) {
    if (typeof v === 'number' && Number.isFinite(v)) out[k] = v;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

export function trackEvent(
  name: string,
  properties?: Props,
  measurements?: Record<string, number>
): void {
  const c = initIfNeeded();
  if (!c) return;
  try {
    c.trackEvent({
      name,
      properties: stringifyProps(properties),
      measurements: numericMeasurements(measurements),
    });
  } catch (err) {
    console.error('trackEvent failed:', err);
  }
}

export function trackException(error: unknown, properties?: Props): void {
  const c = initIfNeeded();
  if (!c) return;
  try {
    const exception =
      error instanceof Error
        ? error
        : new Error(typeof error === 'string' ? error : JSON.stringify(error));
    c.trackException({
      exception,
      properties: stringifyProps(properties),
    });
  } catch (err) {
    console.error('trackException failed:', err);
  }
}

export function trackMetric(name: string, value: number, properties?: Props): void {
  const c = initIfNeeded();
  if (!c) return;
  if (!Number.isFinite(value)) return;
  try {
    c.trackMetric({ name, value, properties: stringifyProps(properties) });
  } catch (err) {
    console.error('trackMetric failed:', err);
  }
}

export function trackRequest(opts: {
  name: string;
  url: string;
  duration: number;
  resultCode: string | number;
  success: boolean;
  source?: string;
  properties?: Props;
}): void {
  const c = initIfNeeded();
  if (!c) return;
  try {
    const properties =
      opts.source !== undefined
        ? { ...(opts.properties ?? {}), source: opts.source }
        : opts.properties;
    c.trackRequest({
      name: opts.name,
      url: opts.url,
      duration: opts.duration,
      resultCode: String(opts.resultCode),
      success: opts.success,
      properties: stringifyProps(properties),
    });
  } catch (err) {
    console.error('trackRequest failed:', err);
  }
}

export function trackDependency(opts: {
  name: string;
  target?: string;
  data?: string;
  duration: number;
  success: boolean;
  resultCode?: string | number;
  dependencyTypeName?: string;
  properties?: Props;
}): void {
  const c = initIfNeeded();
  if (!c) return;
  try {
    c.trackDependency({
      name: opts.name,
      target: opts.target || '',
      data: opts.data || '',
      duration: opts.duration,
      success: opts.success,
      resultCode: String(opts.resultCode ?? (opts.success ? '0' : '1')),
      dependencyTypeName: opts.dependencyTypeName || 'HTTP',
      properties: stringifyProps(opts.properties),
    });
  } catch (err) {
    console.error('trackDependency failed:', err);
  }
}

/**
 * Flush any queued telemetry. Call before short-lived processes exit so events
 * aren't dropped. Safe to call when not configured (no-op).
 */
export async function flush(): Promise<void> {
  const c = initIfNeeded();
  if (!c) return;
  try {
    await c.flush();
  } catch (err) {
    console.error('App Insights flush failed:', err);
  }
}
