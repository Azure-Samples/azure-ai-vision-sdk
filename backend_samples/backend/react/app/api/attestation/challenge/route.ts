import { NextRequest, NextResponse } from 'next/server';
import { withApiTelemetry } from '../../../_lib/next_telemetry';
import { getAttestationService } from '../../../_lib/attestation_service';
import { ROUTES, type AttestationChallengeRequest } from '@azure/ai-vision-face-deviceattestation';

export const POST = withApiTelemetry(ROUTES.challenge, async (request: NextRequest): Promise<NextResponse> => {
  const searchParams = request.nextUrl.searchParams;
  const req: AttestationChallengeRequest = {
    sessionId: searchParams.get('s'),
    clientId: searchParams.get('cid'),
    system: searchParams.get('sys'),
  };

  const result = await getAttestationService().challenge(req);
  return NextResponse.json(result.body, { status: result.status });
});
