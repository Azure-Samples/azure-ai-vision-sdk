/**
 * Telemetry abstraction for the attestation library.
 *
 * The library never depends on a concrete telemetry SDK. The host installs an
 * {@link AttestationLogger} implementation (e.g. App Insights) ONCE at startup
 * via `createAttestationService(config, store, logger)`, and the library emits
 * telemetry through the free functions below, which delegate to the installed
 * logger (defaulting to a no-op so unconfigured/test runs are silent).
 *
 * Call sites keep using `trackEvent` / `trackException` / `trackDependency`
 * exactly as before — only the source of these functions moved from the App
 * Insights module (now app-side) to this framework-agnostic abstraction.
 */

/** Optional structured properties attached to a telemetry item. */
export type TelemetryProps = Record<string, unknown> | undefined;

/** Sink the host provides so the library can emit telemetry without owning an SDK. */
export interface AttestationLogger {
  /** Record a named custom event with optional properties + numeric measurements. */
  trackEvent(name: string, properties?: TelemetryProps, measurements?: Record<string, number>): void;
  /** Record an exception with optional properties. */
  trackException(error: unknown, properties?: TelemetryProps): void;
  /** Record an outbound dependency call (e.g. Play Integrity / revocation HTTP). */
  trackDependency(opts: {
    name: string;
    target?: string;
    data?: string;
    duration: number;
    success: boolean;
    resultCode?: string | number;
    dependencyTypeName?: string;
    properties?: TelemetryProps;
  }): void;
}

const noopLogger: AttestationLogger = {
  trackEvent() {},
  trackException() {},
  trackDependency() {},
};

let logger: AttestationLogger = noopLogger;

/** Install the telemetry logger. Called once by `createAttestationService`. */
export function setAttestationLogger(next: AttestationLogger): void {
  logger = next;
}

export function trackEvent(
  name: string,
  properties?: TelemetryProps,
  measurements?: Record<string, number>,
): void {
  logger.trackEvent(name, properties, measurements);
}

export function trackException(error: unknown, properties?: TelemetryProps): void {
  logger.trackException(error, properties);
}

export function trackDependency(opts: {
  name: string;
  target?: string;
  data?: string;
  duration: number;
  success: boolean;
  resultCode?: string | number;
  dependencyTypeName?: string;
  properties?: TelemetryProps;
}): void {
  logger.trackDependency(opts);
}
