export type LivenessOperationMode = 'Passive' | 'PassiveActive';

// We demonstrate passing the parameters for session creation to a server api on our app-backend created with Next.js
// Please see `app/api/generateAccessToken/route.ts` for the server-side code
export async function fetchTokenFromAPI(
  livenessOperationMode: string,
  sendResultsToClient: boolean,
  file: File | undefined
): Promise<{
    token: string | null;
    message: string | undefined
}> {
  // This is an example of how to generate a liveness session without exposing your api key or endpoint to the client side
  let action = (file !== undefined) ? "detectLivenessWithVerify": "detectLiveness";
  let parameters: { [key: string]: string | boolean } = {
    action,
    livenessOperationMode,
    sendResultsToClient,
    deviceCorrelationId: await getDummyDeviceId(),
  };
  let sessionCreationBody: FormData = new FormData();
  sessionCreationBody.append('Parameters', JSON.stringify(parameters));
  if (action === "detectLivenessWithVerify" && file !== undefined) {
    // When action is detectLivenessWithVerify, file is uploaded, therefore we check liveness and
    // verify that the person in camera is the same person as in the provided image
    sessionCreationBody.append('VerifyImage', file, file.name);
  }

  const res = await fetch(`/api/generateAccessToken`, {
    method: 'POST',
    body: sessionCreationBody,
  });

  const tokenData = await res.json();
  
  // Note: Tokens can fail to generate due to several reasons, including uploading an image that is not a human face.
  if (!res.ok) {
    return {
        token: null,
        message: tokenData.error.message
    }
  }

  return {
    token: tokenData.authToken,
    message: undefined
  }
}

const getDummyDeviceId = async (): Promise<string> => {
  let deviceId = (await navigator.mediaDevices.enumerateDevices()).find(
    (device) => device.deviceId !== ''
  )?.deviceId;

  if (deviceId) {
    deviceId = deviceId.endsWith('=')
      ? Array.from(atob(deviceId), (char) =>
          ('0' + char.charCodeAt(0).toString(16)).slice(-2)
        ).join('')
      : deviceId;
  } else {
    deviceId =
      ((globalThis.crypto as any)?.randomUUID().replace(/-/g, '') as string) || '0'.repeat(64);
  }

  deviceId = '0'.repeat(64 - deviceId.length) + deviceId;
  deviceId = (
    BigInt('0x' + deviceId.substring(0, 32)) ^
    BigInt('0x' + deviceId.substring(32, 64))
  )
    .toString(16)
    .substring(0, 32);
  deviceId =
    ('0'.repeat(32 - deviceId.length) + deviceId)
      .match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/)
      ?.slice(1)
      .join('-') || '';

  return deviceId;
};
