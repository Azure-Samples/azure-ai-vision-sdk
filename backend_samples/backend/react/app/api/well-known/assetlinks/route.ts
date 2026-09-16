import { NextResponse } from 'next/server';
import { getAttestationService } from '../../../_lib/attestation_service';

// Served at /.well-known/assetlinks.json via a rewrite in next.config.mjs.
// Generated from the attestation service configuration.
export const dynamic = 'force-dynamic';

export async function GET() {
  return NextResponse.json(getAttestationService().androidAssetLinks(), {
    headers: {
      'content-type': 'application/json',
      'Cache-Control': 'public, max-age=3600',
    },
  });
}
