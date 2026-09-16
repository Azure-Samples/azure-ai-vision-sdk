/**
 * Redis connection + dependency-telemetry helpers shared between server_utils
 * (session/token storage) and cert_store (cert_thumb:* records).
 *
 * Production: Azure Redis with AAD/Managed Identity, TLS, OID-derived
 * username from the access token.
 * Local dev: USE_LOCAL_REDIS=true connects to 127.0.0.1:6379 with no auth or TLS.
 */
import { createClient } from '@redis/client';
import { ManagedIdentityCredential } from '@azure/identity';
import { AccessToken } from '@azure/core-auth';
import { trackDependency } from '../app_insights_server';

/**
 * Resolve the Redis target hostname:port at call time. Used as the
 * `target` field on dependency telemetry so traces reflect the actual
 * remote — local in dev (USE_LOCAL_REDIS=true), the Azure Redis instance from
 * REDIS_HOSTNAME / REDIS_PORT in production.
 */
function getRedisTarget(): string {
  const useLocal = process.env.USE_LOCAL_REDIS === 'true';
  if (useLocal) {
    return '127.0.0.1:6379';
  }
  const host = process.env.REDIS_HOSTNAME || '127.0.0.1';
  const port = process.env.REDIS_PORT || '6380';
  return `${host}:${port}`;
}

/**
 * Emit one Redis dependency telemetry event. Caller supplies command, key,
 * duration, success, and optional resultCode/properties. Keeps the shape
 * consistent across every Redis op in the codebase.
 */
export function trackRedisOp(opts: {
  command: string;
  key?: string;
  duration: number;
  success: boolean;
  resultCode?: string | number;
  properties?: Record<string, unknown>;
}): void {
  trackDependency({
    name: `Redis.${opts.command}`,
    target: getRedisTarget(),
    data: opts.key ? `${opts.command} ${opts.key}` : opts.command,
    duration: opts.duration,
    success: opts.success,
    resultCode: opts.resultCode ?? (opts.success ? 'OK' : 'ERROR'),
    dependencyTypeName: 'Redis',
    properties: opts.properties,
  });
}

/**
 * AAD access tokens for Azure Redis carry the principal's OID in the JWT
 * payload's `oid` claim, and Azure Redis expects that OID as the username on
 * connect.
 */
function extractUsernameFromToken(accessToken: AccessToken): string {
  const base64Metadata = accessToken.token.split('.')[1];
  const { oid } = JSON.parse(Buffer.from(base64Metadata, 'base64').toString('utf8'));
  return oid;
}

/**
 * Create a Redis client.
 *
 * Returns null on env-config errors (no REDIS_HOSTNAME / bad REDIS_PORT) or
 * if Managed Identity can't obtain a token. The caller is responsible for
 * connecting (and disconnecting in a `finally`).
 */
export async function createRedisClient() {
  // Local dev: USE_LOCAL_REDIS=true connects to 127.0.0.1:6379 with no auth or
  // TLS, so the sample runs against a plain local Redis without Azure/Entra.
  if (process.env.USE_LOCAL_REDIS === 'true') {
    try {
      return createClient({ url: 'redis://127.0.0.1:6379' });
    } catch (error) {
      console.error('Error creating local Redis client:', error);
      return null;
    }
  }

  const redisHost = `${process.env.REDIS_HOSTNAME}`;
  const redisPort = parseInt(process.env.REDIS_PORT || '6380', 10);
  // Azure Redis AAD Scope (works for all regions)
  const redisScope = 'https://redis.azure.com/.default';
  if (!redisHost) {
    console.error('Redis host is not defined. Check the REDIS_HOSTNAME environment variable.');
    return null;
  }

  if (isNaN(redisPort) || redisPort <= 0) {
    console.error(`Invalid redis port: "${process.env.REDIS_PORT}"`);
    return null;
  }
  let client;
  try {
    const credential = new ManagedIdentityCredential();
    const accessToken = await credential.getToken(redisScope);
    if (!accessToken || !accessToken.token) {
      console.error('Invalid access token: Access token is null or missing the token property.');
      return null;
    }

    const username = extractUsernameFromToken(accessToken);
    if (!username) {
      console.error('Username is not defined. Check the access token.');
      return null;
    }

    client = createClient({
      username: username,
      password: accessToken.token,
      url: `rediss://${redisHost}:${redisPort}`,
      socket: {
        tls: true,
      },
    });
  } catch (error) {
    console.error('Error creating Redis client:', error);
    return null;
  }
  return client;
}
