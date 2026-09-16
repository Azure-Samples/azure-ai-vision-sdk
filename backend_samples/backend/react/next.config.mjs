/** @type {import('next').NextConfig} */
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const nextConfig = {
  output: 'standalone',

  // The attestation library is a `file:` dependency that lives outside this app
  // folder (../../library/...). Turbopack resolves that junction to its real
  // path and only pulls in modules under the project root, so pin the root to
  // backend_samples — the nearest ancestor containing both the app and the lib.
  turbopack: {
    root: path.join(__dirname, '..', '..'),
  },

  // Keep the Node-only telemetry SDK out of the webpack bundle; it's loaded from
  // node_modules at runtime (its optional OpenTelemetry sub-deps otherwise
  // trigger bundler "module not found" warnings).
  serverExternalPackages: ['applicationinsights'],

  async rewrites() {
    // The App/Universal Link binding documents live at fixed, dot-prefixed URLs
    // that must serve from the site root. They're generated dynamically from
    // env vars by route handlers under /api/well-known/* and surfaced here.
    return [
      {
        source: '/.well-known/apple-app-site-association',
        destination: '/api/well-known/aasa',
      },
      {
        // Some Apple tooling also probes the legacy root-level path.
        source: '/apple-app-site-association',
        destination: '/api/well-known/aasa',
      },
      {
        source: '/.well-known/assetlinks.json',
        destination: '/api/well-known/assetlinks',
      },
    ];
  },

  async headers() {
    return [
      {
        // Security headers on every route.
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=31536000; includeSubDomains; preload',
          },
        ],
      },
      {
        // The App/Universal Link binding documents must serve as JSON.
        source: '/.well-known/apple-app-site-association',
        headers: [{ key: 'content-type', value: 'application/json' }],
      },
      {
        source: '/.well-known/assetlinks.json',
        headers: [{ key: 'content-type', value: 'application/json' }],
      },
    ];
  },
};

export default nextConfig;
