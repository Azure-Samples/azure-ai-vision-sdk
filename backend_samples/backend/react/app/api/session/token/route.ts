import { NextRequest, NextResponse } from 'next/server';
import { withApiTelemetry } from '../../../_lib/next_telemetry';
import { getAttestationService } from '../../../_lib/attestation_service';
import {
  ROUTES,
  type SessionTokenRequest,
  type SessionTokenBody,
} from '@azure/ai-vision-face-deviceattestation';

export const POST = withApiTelemetry(ROUTES.sessionToken, async (request: NextRequest): Promise<NextResponse> => {
  const searchParams = request.nextUrl.searchParams;

  let body: SessionTokenBody | null = null;
  try {
    body = (await request.json()) as SessionTokenBody;
  } catch {
    body = null;
  }

  const req: SessionTokenRequest = {
    sessionId: searchParams.get('s'),
    body,
  };

  const result = await getAttestationService().sessionToken(req);
  return NextResponse.json(result.body, { status: result.status });
});
