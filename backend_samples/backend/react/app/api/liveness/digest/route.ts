import { NextRequest, NextResponse } from 'next/server';
import { withApiTelemetry } from '../../../_lib/next_telemetry';
import { getAttestationService } from '../../../_lib/attestation_service';
import {
  ROUTES,
  type LivenessDigestRequest,
  type LivenessDigestBody,
} from '@azure/ai-vision-face-deviceattestation';

export const POST = withApiTelemetry(ROUTES.livenessDigest, async (request: NextRequest): Promise<NextResponse> => {
  const searchParams = request.nextUrl.searchParams;

  let body: LivenessDigestBody | null = null;
  try {
    body = (await request.json()) as LivenessDigestBody;
  } catch {
    body = null;
  }

  const req: LivenessDigestRequest = {
    sessionId: searchParams.get('s'),
    body,
  };

  const result = await getAttestationService().livenessDigest(req);
  return NextResponse.json(result.body, { status: result.status });
});
