import { NextRequest, NextResponse } from 'next/server';
import { withApiTelemetry } from '../../../_lib/next_telemetry';
import { getAttestationService } from '../../../_lib/attestation_service';
import {
  ROUTES,
  type AttestationRegisterRequest,
  type AttestationRegisterBody,
} from '@azure/ai-vision-face-deviceattestation';

export const POST = withApiTelemetry(ROUTES.register, async (request: NextRequest): Promise<NextResponse> => {
  const searchParams = request.nextUrl.searchParams;

  let body: AttestationRegisterBody | null = null;
  try {
    body = (await request.json()) as AttestationRegisterBody;
  } catch {
    body = null;
  }

  const req: AttestationRegisterRequest = {
    sessionId: searchParams.get('s'),
    clientId: searchParams.get('cid'),
    system: searchParams.get('sys'),
    body,
  };

  const result = await getAttestationService().register(req);

  // Print the iOS App Attest receipt (base64) so it can be copied off the
  // server and exchanged with Apple's App Attest data endpoint (DeviceCheck)
  // to read the device fraud-risk metric.
  if (result.data?.platform === 'ios') {
  //  console.log('[register] iOS App Attest receipt (base64)', {
  //    sessionId: req.sessionId,
  //    receipt: result.data.appAttestVerdict?.receipt ?? null,
  //  });
  }

  return NextResponse.json(result.body, { status: result.status });
});
