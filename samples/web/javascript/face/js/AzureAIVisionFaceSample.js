// Step 1: Import the web component.
import "./azure-ai-vision-face-ui/FaceLivenessDetector.js";
import { LivenessStatus, RecognitionStatus } from "./azure-ai-vision-face-ui/FaceLivenessDetector.js"; 
import { personIcon, heartPulseIcon, checkmarkCircleIcon, dismissCircleIcon, createOrUpdateFeedbackItem, createOrUpdateLine } from './Utils.js';

// Setup steps:
// - prevent zooming
window.addEventListener("touchmove", function (event) { if (event.scale !== 1) { event.preventDefault(); } }, { passive: false });
window.addEventListener("wheel", function (event) { if (event.scale !== 1) { event.preventDefault(); } }, { passive: false });

const startPassiveButton = document.getElementById('startPassiveButton');
const startPassiveActiveButton = document.getElementById('startPassiveActiveButton');
const continueButton = document.getElementById('continueButton');
const useVerifyImageFileInput = document.getElementById('useVerifyImageFileInput');
const verifyImageRow = document.getElementById('verifyImageRow');
const feedbackContainer = document.getElementById('feedbackContainer');

async function getDummyDeviceId() {
  var length = 10;
  var characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  var charactersLength = characters.length;
  var result = '';

  for (var i = 0; i < length; i++) {
      var randomIndex = Math.floor(Math.random() * charactersLength);
      result += characters.charAt(randomIndex);
  }

  return result;
}

function restartFaceLiveness() {
  splash.toggleAttribute('hidden');
  startPassiveButton.toggleAttribute("hidden");
  startPassiveActiveButton.toggleAttribute("hidden");
  verifyImageRow.toggleAttribute("hidden");
  continueButton.toggleAttribute("hidden");
  feedbackContainer.setAttribute('hidden', true);
  feedbackContainer.style.display = "none";
}

// This method shows how to start face liveness check.
async function startFaceLiveness(event) {
  splash.setAttribute('hidden', null);
  startPassiveButton.toggleAttribute("hidden");
  startPassiveActiveButton.toggleAttribute("hidden");
  verifyImageRow.toggleAttribute("hidden");
  feedbackContainer.setAttribute('hidden', true);
  feedbackContainer.style.display = "none";

  // Step 2: query the azure-ai-vision-face-ui element to process face liveness.
  // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
  // An example would be to retry in case of a failure.
  var faceLivenessDetector = document.querySelector("azure-ai-vision-face-ui");

  // Step 3: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

  // Note: The liveness-operation-mode is retrieved from 'data-mode' attribute of the start buttons, for more information: https://aka.ms/face-api-reference-livenessoperationmode
  const livenessOperationMode = event.target.dataset.mode;
  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  const sessionBodyStruct = { livenessOperationMode: livenessOperationMode, deviceCorrelationId: await getDummyDeviceId() };
  let sessionCreationBody = JSON.stringify(sessionBodyStruct);
  let sessionCreationHeaders = { 'Content-Type': 'application/json' };
  let action = "detectLiveness";
  // Note2: You can also create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
  if (useVerifyImageFileInput.files.length > 0) {
    sessionCreationBody = new FormData();
    sessionCreationBody.append("verifyImage", useVerifyImageFileInput.files[0], useVerifyImageFileInput.files[0].name);
    sessionCreationBody.append("livenessOperationMode", livenessOperationMode);
    sessionCreationBody.append("deviceCorrelationId", await getDummyDeviceId());
    sessionCreationHeaders = {};
    action = "detectLivenessWithVerify";
  }
  // Calling a mocked app backend to create the session, this part is left to the developer to implement in a production setting. Code samples are provided in https://aka.ms/azure-ai-vision-face-liveness-tutorial
  const session = await (await fetch(`/api/${action}-sessions`, { method: "POST", headers: sessionCreationHeaders, body: sessionCreationBody, })).json();

  if (faceLivenessDetector == null) {
    // Step 4: Create the face liveness detector element and attach it to DOM.
    faceLivenessDetector = document.createElement("azure-ai-vision-face-ui");
    document.getElementById("container").appendChild(faceLivenessDetector);
  }
   
  // For multi-camera scenarios, you can set desired deviceId by using following APIs:
  // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method.
  // You can then set the desired deviceId as an attribute faceLivenessDetector.mediaInfoDeviceId = <desired-device-id>

  // Step 5: Start the face liveness check session and handle the promise returned appropriately.
  faceLivenessDetector.start(session.authToken)
  .then(async resultData => {
    console.log(resultData);
    // Once the session is completed and promise fulfilled, the client does not receive the outcome whether face is live or spoof.
    // You can query the result from your backend service by calling the sessions results API
    // https://aka.ms/face/liveness-session/get-liveness-session-result
    const sessionResult = await (await fetch(`/api/${action}-sessions/${session.sessionId}`, { method: "GET", headers: sessionCreationHeaders })).json();
    const livenessStatus = sessionResult?.results?.attempts?.[0]?.result?.livenessDecision ?? sessionResult?.result?.response?.body?.livenessDecision ?? null;
    const livenessCondition = livenessStatus == "realface";
    const livenessIcon = livenessCondition ? checkmarkCircleIcon : dismissCircleIcon;
    
    let livenessText = null;
    if (livenessCondition) {
      livenessText = "Real Person";
    }
    else {
      livenessText = "Spoof";
    }
    
    createOrUpdateFeedbackItem("liveness-icon", heartPulseIcon, "Liveness");
    createOrUpdateFeedbackItem("liveness-status", livenessIcon, livenessText);

    // For scenario that requires face verification, the resultData.recognitionResult contains the result of the face verification.
    if (action == "detectLivenessWithVerify") {
      const verificationCondition = sessionResult?.results?.attempts?.[0]?.result?.verifyResult?.isIdentical ?? sessionResult?.result?.response?.body?.verifyResult?.isIdentical ?? null;
      const verificationIcon = verificationCondition ? checkmarkCircleIcon : dismissCircleIcon;
      let verificationText = null;
        
      if (verificationCondition) {
        verificationText = "Same Person";
      }
      else {
        verificationText = "Not the same person";
      }
    
      createOrUpdateLine("separator-line");
      createOrUpdateFeedbackItem('verification-icon', personIcon, 'Verification');
      createOrUpdateFeedbackItem('verification-status', verificationIcon, verificationText);
    }
    // - Show continue button so user can restart the liveness check.
    continueButton.toggleAttribute("hidden");
    feedbackContainer.style.display = "flex";
    feedbackContainer.removeAttribute('hidden');
  })
  .catch(errorData => {
    // In case of failures, the promise is rejected. The errorData contains the reason for the failure.
    const livenessText = errorData.livenessError;
    const livenessIcon = dismissCircleIcon;
    createOrUpdateFeedbackItem("liveness-icon", heartPulseIcon, "Liveness");
    createOrUpdateFeedbackItem("liveness-status", livenessIcon, livenessText);

    if (action == "detectLivenessWithVerify") {
      const verificationText = errorData.recognitionError;
      const verificationIcon = dismissCircleIcon;
      createOrUpdateLine("separator-line");
      createOrUpdateFeedbackItem('verification-icon', personIcon, 'Verification');
      createOrUpdateFeedbackItem('verification-status', verificationIcon, verificationText);
    }
  
    // - Show continue button so user can restart the liveness check.
    continueButton.toggleAttribute("hidden");
    feedbackContainer.style.display = "flex";
    feedbackContainer.removeAttribute('hidden');
  });
}

// Step 8: Wire up face liveness check start method to the start buttons.
startPassiveButton.addEventListener('click', startFaceLiveness);
startPassiveActiveButton.addEventListener('click', startFaceLiveness);
continueButton.addEventListener('click', restartFaceLiveness);