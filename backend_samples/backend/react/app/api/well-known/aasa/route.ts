import { NextResponse } from 'next/server';
import { getAttestationService } from '../../../_lib/attestation_service';

// Served at /.well-known/apple-app-site-association (and the legacy root path)
// via rewrites in next.config.mjs. Generated from environment variables so a
// fresh deployment only needs app settings, not a code change, to bind to a
// new app identity.
export const dynamic = 'force-dynamic';

export async function GET() {
  return NextResponse.json(getAttestationService().appleAppSiteAssociation(), {
    headers: {
      'content-type': 'application/json',
      'Cache-Control': 'public, max-age=3600',
    },
  });
}
