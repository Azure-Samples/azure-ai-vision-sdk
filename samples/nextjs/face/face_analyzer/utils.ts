export const fetchToken = async (
  faceApiEndPoint: string,
  faceApiKey: string,
  livenessOperationMode: string,
  sendResultsToClient: boolean,
  setToken: (token: string) => void
): Promise<void> => {
  try {
    const response = await fetch(
      `${faceApiEndPoint}/face/v1.1-preview.1/detectLiveness/singleModal/sessions`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Ocp-Apim-Subscription-Key": faceApiKey,
        },
        body: JSON.stringify({
          livenessOperationMode: livenessOperationMode,
          sendResultsToClient: sendResultsToClient,
          deviceCorrelationId: await getDummyDeviceId(),
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch the token. Status: ${response.status}`);
    }

    const sessions = await response.json();
    setToken(sessions.authToken);
  } catch (error) {
    console.log(error);
  }
};

const getDummyDeviceId = async (): Promise<string> => {
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
