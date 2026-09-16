import { NextRequest, NextResponse } from 'next/server';
import { withApiTelemetry } from '../../../_lib/next_telemetry';
import { getAttestationService } from '../../../_lib/attestation_service';
import {
  ROUTES,
  type AttestationVerifyRequest,
  type AttestationVerifyBody,
} from '@azure/ai-vision-face-deviceattestation';

export const POST = withApiTelemetry(ROUTES.verify, async (request: NextRequest): Promise<NextResponse> => {
  const searchParams = request.nextUrl.searchParams;

  let body: AttestationVerifyBody | null = null;
  try {
    body = (await request.json()) as AttestationVerifyBody;
  } catch {
    body = null;
  }

  const req: AttestationVerifyRequest = {
    sessionId: searchParams.get('s'),
    clientId: searchParams.get('cid'),
    system: searchParams.get('sys'),
    body,
  };

  const result = await getAttestationService().verify(req);
  return NextResponse.json(result.body, { status: result.status });
});
