// This is the server-side code of an example for generating access tokens without exposing API endpoint and key to client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
export async function POST(request: Request) {
  const formData = await request.formData();
  const paramString = formData.get("parameters");
  const file = formData.get("verifyImage") as File | null;
  if (typeof paramString !== "string") {
    return Response.json(
      { message: "Parameters not formatted correctly", token: null },
      { status: 400 }
    );
  }

  const parameters = await JSON.parse(paramString);
  const livenessOperationMode = parameters.livenessOperationMode;
  const deviceCorrelationId = parameters.deviceCorrelationId;
  const action = formData.get("Action");

  if (file == undefined && action == "detectLivenessWithVerify") {
    return Response.json(
      {
        message: "VerifyImage not provided for detectLivenessWithVerify",
        token: null,
      },
      { status: 400 }
    );
  }

  // Ensure parameters are within expectation
  if (!(action == "detectLiveness" || action == "detectLivenessWithVerify")) {
    return Response.json(
      {
        message: "action parameter not expected",
        token: null,
      },
      { status: 400 }
    );
  }
  if (
    !(
      livenessOperationMode == "Passive" ||
      livenessOperationMode == "PassiveActive"
    )
  ) {
    return Response.json(
      {
        message: "livenessOperationMode parameter not expected",
        token: null,
      },
      { status: 400 }
    );
  }
  if (typeof deviceCorrelationId != "string") {
    return Response.json(
      {
        message: "deviceCorrelationId parameter not expected",
        token: null,
      },
      { status: 400 }
    );
  }

  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  let requestBody: FormData | string = JSON.stringify({
    livenessOperationMode,
    deviceCorrelationId,
  });

  // Note2: You can create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
  if (action == "detectLivenessWithVerify" && file != undefined) {
    requestBody = new FormData();
    requestBody.append("verifyImage", file, file.name);
    requestBody.append("livenessOperationMode", livenessOperationMode);
    requestBody.append("deviceCorrelationId", deviceCorrelationId);
  }

  // Token is fetched with API endpoint and key
  // On server-side, the endpoint and key can be safely accessed without exposure to client-side
  // detectLiveness takes a stringified JSON object and detectLivenessWithVerify takes a multipart form
  const result = await fetchTokenOnServer(
    process.env.FACE_ENDPOINT!,
    process.env.FACE_KEY!,
    action,
    requestBody
  );

  return Response.json(result, {
    status: result.hasOwnProperty("error") ? 400 : 200,
  });
}

// Fetch Token Function
const fetchTokenOnServer = async (
  faceApiEndPoint: string,
  faceApiKey: string,
  action: string,
  requestBody: FormData | string
) => {
  try {
    let headers: { [key: string]: string } = {
      "Ocp-Apim-Subscription-Key": faceApiKey,
      "X-MS-AZSDK-Telemetry": "sample=nextjs-face-web-sdk",
    };
    if (action === "detectLiveness") {
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch(
      `${faceApiEndPoint}/face/v1.2/${action}-sessions`,
      {
        method: "POST",
        headers: headers,
        body: requestBody,
      }
    );

    const sessions = await response.json();

    if (!response.ok) {
      throw new Error(sessions.error?.message);
    }

    return { sessionData: sessions, message: "success" };
  } catch (error) {
    if (typeof error === "string") {
      return { error: { token: null, message: error } };
    } else if (error instanceof Error) {
      return {
        error: { token: null, message: error.message ?? "Unknown error" },
      };
    }
    return { error: { token: null, message: "Unknown error" } };
  }
};
