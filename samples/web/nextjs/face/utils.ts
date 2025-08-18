export interface SessionAuthorizationData {
  authToken: string;
  sessionId: string; 
}

// DISCLAIMER: In your production environment, you should fetch the token on your app-backend and pass down the session-authorization-token down to the frontend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
// Please see `server.js` for an example of the server-side code.
export async function fetchTokenFromAPI(
  livenessOperationMode: string,
  file: File | undefined,
  setSessionData: (sessionData: SessionAuthorizationData) => void,
  setLoadingToken?: (b: boolean) => void,
  setErrorMessage?: (msg: string) => void
): Promise<void> {
  // This is an example of how to generate a liveness session without exposing your api key or endpoint to the client side
  let action = (file !== undefined) ? "detectLivenessWithVerify": "detectLiveness";
  let parameters: { [key: string]: string | boolean } = {
    livenessOperationMode,
    deviceCorrelationId: await getDummyDeviceId(),
    userCorrelationId: await getDummyUserId(),
  };
  let sessionCreationBody: FormData = new FormData();
  sessionCreationBody.append('Action', action);
  sessionCreationBody.append('parameters', JSON.stringify(parameters));
  if (action === "detectLivenessWithVerify" && file !== undefined) {
    // When action is detectLivenessWithVerify, file is uploaded, therefore we check liveness and
    // verify that the person in camera is the same person as in the provided image
    sessionCreationBody.append('verifyImage', file, file.name);
  }

  const res = await fetch(`/api/generateAccessToken`, {
    method: 'POST',
    body: sessionCreationBody,
  });

  const sessionData = await res.json();

  // Note: Tokens can fail to generate due to several reasons, including uploading an image that is not a human face.
  if (!res.ok) {
    if (setErrorMessage) {
      setErrorMessage(sessionData.error.message);
    }
    if (setLoadingToken) {
      setLoadingToken(false);
    }
    return;
  }

  setSessionData(sessionData.sessionData);
}

export type SessionResponse = {
  token: null;
  message: string;
}

export async function fetchSessionResultFromAPI(
  action: string,
  sessionId: string
) {
  const res = await fetch(`/api/getSessionResult?action=${action}&sessionId=${sessionId}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json'
    }
  });

  const sessionData = await res.json();
  return sessionData;
}

export const getDummyUserId = async (): Promise<string> => {
  let userId =
      globalThis.crypto?.randomUUID()?.replace(/-/g, '') || '0'.repeat(64);

  userId = '0'.repeat(64 - userId.length) + userId;
  userId = (
    BigInt('0x' + userId.substring(0, 32)) ^
    BigInt('0x' + userId.substring(32, 64))
  )
    .toString(16)
    .substring(0, 32);
  userId =
    ('0'.repeat(32 - userId.length) + userId)
      .match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/)
      ?.slice(1)
      .join('-') || '';

  return userId;
};

export const getDummyDeviceId = async (): Promise<string> => {
  let deviceId = (await navigator.mediaDevices.enumerateDevices()).find(
    (device) => device.deviceId !== ""
  )?.deviceId;

  if (deviceId) {
    deviceId = deviceId.endsWith("=")
      ? Array.from(atob(deviceId), (char) =>
          ("0" + char.charCodeAt(0).toString(16)).slice(-2)
        ).join("")
      : deviceId;
  } else {
    deviceId =
      globalThis.crypto?.randomUUID()?.replace(/-/g, "") || "0".repeat(64);
  }

  deviceId = "0".repeat(64 - deviceId.length) + deviceId;
  deviceId = (
    BigInt("0x" + deviceId.substring(0, 32)) ^
    BigInt("0x" + deviceId.substring(32, 64))
  )
    .toString(16)
    .substring(0, 32);
  deviceId =
    ("0".repeat(32 - deviceId.length) + deviceId)
      .match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/)
      ?.slice(1)
      .join("-") || "";

  return deviceId;
};
