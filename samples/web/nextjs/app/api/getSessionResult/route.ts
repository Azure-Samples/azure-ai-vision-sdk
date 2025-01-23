// This is the server-side code example for fetching final results of a liveness session without exposing API endpoint and key to the client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app backend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get('action');
  const sessionId = searchParams.get('sessionId');
  // Session results are fetched with API endpoint and key
  // On server-side, the endpoint and key can be safely accessed without exposure to client-side
  const result = await fetchSessionResultOnServer(
    process.env.FACE_ENDPOINT!,
    process.env.FACE_KEY!,
    action,
    sessionId
  );

  return Response.json(result.sessionResult, {
    status: result.hasOwnProperty("error") ? 400 : 200,
  });
}

// Fetch Session Result Function
const fetchSessionResultOnServer = async (
  faceApiEndPoint: string,
  faceApiKey: string,
  action: string | null,
  sessionId: string | null
) => {
  try {
    let headers: { [key: string]: string } = {
      "Ocp-Apim-Subscription-Key": faceApiKey,
      "X-MS-AZSDK-Telemetry": "sample=nextjs-face-web-sdk",
      "Content-Type": "application/json",
    };

    const response = await fetch(
      `${faceApiEndPoint}/face/v1.2/${action}-sessions/${sessionId}`,
      {
        method: "GET",
        headers: headers,
      }
    );

    const result = await response.json();
    if (!response.ok) {
      throw new Error(result.error?.message);
    }

    return { sessionResult: result, message: "success" };
  } catch (error) {
    if (typeof error === "string") {
      return { error: { sessionResult: null, message: error } };
    } else if (error instanceof Error) {
      return {
        error: { sessionResult: null, message: error.message ?? "Unknown error" },
      };
    }
    return { error: { sessionResult: null, message: "Unknown error" } };
  }
};
