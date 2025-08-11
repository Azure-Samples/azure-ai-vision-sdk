const express = require("express");
const multer = require("multer");
const dotenv = require("dotenv");

dotenv.config({ path: ".env.local" });

const app = express();
const port = process.env.PORT || 3000;

app.use(express.urlencoded({ extended: false }));
app.use(express.json());

// We use multer to receive and process the verify image sent from the client for detectLivenessWithVerify
const storage = multer.memoryStorage();
const upload = multer({ storage: storage });

app.get("/", (_req, res) => {
  res.sendFile(`${__dirname}/dist/index.html`);
});

// This is the server-side code example for fetching final results of a liveness session without exposing API endpoint and key to the client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app backend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
app.get('/api/getSessionResult', async (req, res) => {
  const action = req.query.action;
  const sessionId = req.query.sessionId;
  // Session results are fetched with API endpoint and key
  // On server-side, the endpoint and key can be safely accessed without exposure to client-side
  const result = await fetchSessionResultOnServer(
    process.env.FACE_ENDPOINT,
    process.env.FACE_KEY,
    action,
    sessionId
  );
  res.status(result.hasOwnProperty("error") ? 400 : 200).send(result);
  return;
});

// This is the server-side code example for generating access tokens of a liveness session without exposing API endpoint and key to the client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app backend and pass down the session-authorization-token down to the frontend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
app.post(
  "/api/generateAccessToken",
  upload.single("verifyImage"),
  async (req, res) => {
    let file = null;
    const parameters = JSON.parse(req.body.parameters);
    const livenessOperationMode = parameters.livenessOperationMode;
    const deviceCorrelationId = parameters.deviceCorrelationId;
    const userCorrelationId = parameters.userCorrelationId;
    const action = req.body.Action;

    if (req.file) {
      file = new Blob([req.file.buffer], { name: req.file.originalname });
    }

    if (file == undefined && action == "detectLivenessWithVerify") {
      res.status(400).send({
        message: "VerifyImage not provided for detectLivenessWithVerify",
        token: null,
      });
      return;
    }

    // Ensure parameters are within expectation
    if (!(action == "detectLiveness" || action == "detectLivenessWithVerify")) {
      return res.status(400).send({
        message: "action parameter not expected",
        token: null,
      });
    }
    if (
      !(
        livenessOperationMode == "Passive" ||
        livenessOperationMode == "PassiveActive"
      )
    ) {
      return res.status(400).send({
        message: "livenessOperationMode parameter not expected",
        token: null,
      });
    }
    if (typeof deviceCorrelationId != "string") {
      return res.status(400).send({
        message: "deviceCorrelationId parameter not expected",
        token: null,
      });
    }
    if (typeof userCorrelationId != "string") {
      return res.status(400).send({
        message: "userCorrelationId parameter not expected",
        token: null,
      });
    } 

    // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
    let requestBody = JSON.stringify({
      livenessOperationMode,
      deviceCorrelationId,
      userCorrelationId,
      enableSessionImage:true
    });

    // Note2: You can create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
    if (action == "detectLivenessWithVerify") {
      requestBody = new FormData();
      requestBody.append("verifyImage", file, file.name);
      requestBody.append("livenessOperationMode", livenessOperationMode);
      requestBody.append("deviceCorrelationId", deviceCorrelationId);
      requestBody.append("userCorrelationId", userCorrelationId);
      requestBody.append("enableSessionImage", true);
    }

    // Token is fetched with API endpoint and key
    // On server-side, the endpoint and key can be safely accessed without exposure to client-side
    // detectLiveness takes a stringified JSON object and detectLivenessWithVerify takes a multipart form
    const result = await fetchTokenOnServer(
      process.env.FACE_ENDPOINT,
      process.env.FACE_KEY,
      action,
      requestBody
    );
    res.status(result.hasOwnProperty("error") ? 400 : 200).send(result);
    return;
  }
);

app.use(express.static(`${__dirname}/dist/`));

app.listen(port, () => {
  console.log(`Server started at http://localhost:${port}`);
});

// Fetch Token Function
const fetchTokenOnServer = async (
  faceApiEndPoint,
  faceApiKey,
  action,
  requestBody
) => {
  try {
    let headers = {
      "Ocp-Apim-Subscription-Key": faceApiKey,
      "X-MS-AZSDK-Telemetry": "sample=vuejs-face-web-sdk",
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

const fetchSessionResultOnServer = async (
  faceApiEndPoint,
  faceApiKey,
  action,
  sessionId
) => {
  let headers = {
    "Ocp-Apim-Subscription-Key": faceApiKey,
    "X-MS-AZSDK-Telemetry": "sample=angular-face-web-sdk",
    "Content-Type": "application/json"
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
    throw new Error(sessions.error?.message);
  }

  return { sessionResult: result, message: "success" };
}
