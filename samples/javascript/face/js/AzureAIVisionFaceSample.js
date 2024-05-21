// Step 1: Import the web component.
import "./azure-ai-vision-faceanalyzer/AzureAIVisionFaceAnalyzerEntry.js";
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
  let deviceId = (await navigator.mediaDevices.enumerateDevices()).find(device => device.deviceId !== '')?.deviceId;
  deviceId = deviceId?.endsWith('=') && Array.from(atob(deviceId), (char) => ('0' + char.charCodeAt(0).toString(16)).slice(-2)).join('') || deviceId;
  deviceId = deviceId || globalThis.crypto?.randomUUID?.().match(/[^-]/g).join("") || '0'.repeat(64);
  deviceId = '0'.repeat(64 - deviceId.length) + deviceId;
  deviceId = (BigInt('0x' + deviceId.substring(0, 32)) ^ BigInt('0x' + deviceId.substring(32, 64))).toString(16).substring(0, 32);
  deviceId = ('0'.repeat(32 - deviceId.length) + deviceId).match(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/).slice(1).join('-');
  return deviceId;
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

  // Step 2: query the azure-ai-vision-faceanalyzer element to process face liveness.
  // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
  // An example would be to retry in case of a failure.
  var azureAIVisionFaceAnalyzer = document.querySelector("azure-ai-vision-faceanalyzer");

  // Step 3: Obtain session-authorization-token.
  // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
  // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

  // Note: The liveness-operation-mode is retrieved from 'data-mode' attribute of the start buttons, for more information: https://aka.ms/face-api-reference-livenessoperationmode
  const livenessOperationMode = event.target.dataset.mode;
  // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
  const sessionBodyStruct = { livenessOperationMode: livenessOperationMode, sendResultsToClient: true, deviceCorrelationId: await getDummyDeviceId() };
  let sessionCreationBody = JSON.stringify(sessionBodyStruct);
  let sessionCreationHeaders = { 'Content-Type': 'application/json', 'Ocp-Apim-Subscription-Key':'***Face_API_KEY***' };
  let action = "detectLiveness";
  // Note2: You can also create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
  if (useVerifyImageFileInput.files.length > 0) {
    sessionCreationBody = new FormData();
    sessionCreationBody.append("Parameters", JSON.stringify(sessionBodyStruct));
    sessionCreationBody.append("VerifyImage", useVerifyImageFileInput.files[0], useVerifyImageFileInput.files[0].name);
    sessionCreationHeaders = {};
    action = "detectLivenessWithVerify";
  }
  // Calling a mocked app backend to create the session, this part is left to the developer to implement in a production setting. Code samples are provided in https://aka.ms/azure-ai-vision-face-liveness-tutorial
  const session = await (await fetch(`***FACE_API_ENDPOINT***/face/v1.1-preview.1/${action}/singleModal/sessions`, { method: "POST", headers: sessionCreationHeaders, body: sessionCreationBody, })).json();

  if (azureAIVisionFaceAnalyzer == null) {
    // Step 4: Create the faceanalyzer element, set the token and upgrade the element.
    azureAIVisionFaceAnalyzer = document.createElement("azure-ai-vision-faceanalyzer");
    customElements.upgrade(azureAIVisionFaceAnalyzer);
    azureAIVisionFaceAnalyzer.token = session.authToken;

    // For multi-camera scenarios, you can set desired deviceId by using following APIs
    // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
    // Once you have list of camera devices you must call azureAIVisionFaceAnalyzer.filterToSupportedMediaInfoDevices() to get list of devices supported by AzureAIVisionFaceAnalyzer
    // You can then set the desired deviceId as an attribute azureAIVisionFaceAnalyzer.mediaInfoDeviceId = <desired-device-id>

    // Step 5: Handle analysis complete event
    // Note: For added security, you are not required to trust the 'status' property from client.
    // Your backend can and should verify this by querying about the session Face API directly.
    azureAIVisionFaceAnalyzer.addEventListener('analyzed', (event) => {
      // The event.result.livenessResult contains the result of the analysis.
      // The result contains the following properties:
      // - status: The result of the liveness detection.
      // - failureReason: The reason for the failure if the status is LivenessStatus.Failed.
      const livenessStatus = azureAIVisionFaceAnalyzer.LivenessStatus[event.result.livenessResult.status];
      const livenessCondition = livenessStatus == "Live";
      const livenessIcon = livenessCondition ? checkmarkCircleIcon : dismissCircleIcon;
      
      let livenessText = null;
      if (livenessStatus == "Live") {
        livenessText = "Live Person";
      }
      else if (livenessStatus == "Spoof") {
        livenessText = "Spoof";
      }
      else if (livenessStatus == "CompletedResultQueryableFromService") {
        livenessText = "CompletedResultQueryableFromService";
      }
      else {
        livenessText = azureAIVisionFaceAnalyzer.LivenessFailureReason[event.result.livenessResult.failureReason];
      }
      
      createOrUpdateFeedbackItem("liveness-icon", heartPulseIcon, "Liveness");
      createOrUpdateFeedbackItem("liveness-status", livenessIcon, livenessText);

      // For scenario that requires face verification, the event.result.recognitionResult contains the result of the face verification.
      if (event.result.recognitionResult.resultId != "") {
        const verificationStatus = azureAIVisionFaceAnalyzer.RecognitionStatus[event.result.recognitionResult.status];
        const verificationCondition = verificationStatus == "Recognized";
        const verificationIcon = verificationCondition ? checkmarkCircleIcon : dismissCircleIcon;
        let verificationText = null;
        
        if (verificationStatus == "Recognized") {
          verificationText = "Matched";
        }
        else if (verificationStatus == "NotRecognized") {
          verificationText = "Not Matched";
        }
        else if (verificationStatus == "CompletedResultQueryableFromService") {
          verificationText = "CompletedResultQueryableFromService";
        }
        else {
          verificationText = azureAIVisionFaceAnalyzer.RecognitionFailureReason[event.result.recognitionResult.failureReason];
        }

        createOrUpdateLine("separator-line");
        createOrUpdateFeedbackItem('verification-icon', personIcon, 'Verification');
        createOrUpdateFeedbackItem('verificatio-status', verificationIcon, verificationText);
      }

      // - Show continue button so user can restart the liveness check.
      continueButton.toggleAttribute("hidden");
      feedbackContainer.style.display = "flex";
      feedbackContainer.removeAttribute('hidden');
    });

    // Step 6: Add the element to the DOM.
    document.getElementById("container").appendChild(azureAIVisionFaceAnalyzer);
  }
  else {
    // Step 7: In order to retry the session, in case of failure, you can use analyzeOnceAgain().
    // Make sure to set a new token for the next session.
    // For multi-camera scenarios, you need to set the deviceId again by following the aforementioned procedure 
    // in Step 4
    azureAIVisionFaceAnalyzer.token = session.authToken;
    await azureAIVisionFaceAnalyzer.analyzeOnceAgain();
  }
}

// Step 8: Wire up face liveness check start method to the start buttons.
startPassiveButton.addEventListener('click', startFaceLiveness);
startPassiveActiveButton.addEventListener('click', startFaceLiveness);
continueButton.addEventListener('click', restartFaceLiveness);
