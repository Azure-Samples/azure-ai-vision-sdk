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

// **IMPORTANT**: This middleware sets headers for all responses, static or not.
app.all("*", function (req, res, next) {
  res.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
  res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
  next();
});

app.get("/", (_req, res) => {
  res.sendFile(`${__dirname}/dist/index.html`);
});

// This is the server-side code example for generating access tokens of a liveness session without verify image, and without exposing API endpoint and key to the client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
app.post("/api/detectLiveness/singleModal/sessions", async (req, res) => {
  const parameters = req.body;
  const livenessOperationMode = parameters.livenessOperationMode;
  const sendResultsToClient = parameters.sendResultsToClient;
  const deviceCorrelationId = parameters.deviceCorrelationId;

  // Ensure parameters are within expectation
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
  if (typeof sendResultsToClient != "boolean") {
    return res.status(400).send({
      message: "sendResultsToClient parameter not expected",
      token: null,
    });
  }
  if (typeof deviceCorrelationId != "string") {
    return res.status(400).send({
      message: "deviceCorrelationId parameter not expected",
      token: null,
    });
  }

  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  let formBody = JSON.stringify({
    livenessOperationMode,
    sendResultsToClient,
    deviceCorrelationId,
  });

  // Token is fetched with API endpoint and key
  // On server-side, the endpoint and key can be safely accessed without exposure to client-side
  const result = await fetchTokenOnServer(
    process.env.FACE_ENDPOINT,
    process.env.FACE_KEY,
    "detectLiveness", // detectLiveness for this endpoint
    formBody // string for detectLiveness body
  );
  res.status(result.hasOwnProperty("error") ? 400 : 200).send(result);
});

// This is the server-side code example for generating access tokens of a liveness session with verify image without exposing API endpoint and key to the client-side.
// DISCLAIMER: In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend
// For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
app.post(
  "/api/detectLivenessWithVerify/singleModal/sessions",
  upload.single("VerifyImage"),
  async (req, res) => {
    const file = new Blob([req.file.buffer], { name: req.file.originalname });
    const parameters = JSON.parse(req.body.Parameters);
    const livenessOperationMode = parameters.livenessOperationMode;
    const sendResultsToClient = parameters.sendResultsToClient;
    const deviceCorrelationId = parameters.deviceCorrelationId;

    if (file == null) {
      res.status(400).send({
        message: "VerifyImage not provided for detectLivenessWithVerify",
        token: null,
      });
      return;
    }

    // Ensure parameters are within expectation
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
    if (typeof sendResultsToClient != "boolean") {
      return res.status(400).send({
        message: "sendResultsToClient parameter not expected",
        token: null,
      });
    }
    if (typeof deviceCorrelationId != "string") {
      return res.status(400).send({
        message: "deviceCorrelationId parameter not expected",
        token: null,
      });
    }

    // Note2: You can also create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
    let formBody = new FormData();
    formBody.append(
      "Parameters",
      JSON.stringify({
        livenessOperationMode,
        sendResultsToClient,
        deviceCorrelationId,
      })
    );
    formBody.append("VerifyImage", file, file.name);

    // Token is fetched with API endpoint and key
    // On server-side, the endpoint and key can be safely accessed without exposure to client-side
    const result = await fetchTokenOnServer(
      process.env.FACE_ENDPOINT,
      process.env.FACE_KEY,
      "detectLivenessWithVerify", // detectLivenessWithVerify for this endpoint
      formBody // FormData for detectLivenessWithVerify
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
const fetchTokenOnServer = async (faceApiEndPoint, faceApiKey, action, formBody) => {
  try {
    let headers = { "Ocp-Apim-Subscription-Key": faceApiKey };
    if (action === "detectLiveness") {
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch(
      `${faceApiEndPoint}/face/v1.1-preview.1/${action}/singleModal/sessions`,
      {
        method: "POST",
        headers: headers,
        body: formBody,
      }
    );

    const sessions = await response.json();

    if (!response.ok) {
      throw new Error(sessions.error?.message);
    }

    return { authToken: sessions.authToken, message: "success" };
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
