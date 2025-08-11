// Step 1: Import the web component.
import "./@azure/ai-vision-face-ui/FaceLivenessDetector.js";
import { personIcon, heartPulseIcon, checkmarkCircleIcon, dismissCircleIcon, createOrUpdateFeedbackItem, createOrUpdateLine } from './Utils.js';

// Setup steps:
// - prevent zooming
window.addEventListener("touchmove", function (event) { if (event.scale !== 1) { event.preventDefault(); } }, { passive: false });
window.addEventListener("wheel", function (event) { if (event.scale !== 1) { event.preventDefault(); } }, { passive: false });

const startPassiveButton = document.getElementById('startPassiveButton');
const startPassiveActiveButton = document.getElementById('startPassiveActiveButton');
const continueButton = document.getElementById('continueButton');
const retryButton = document.getElementById('retryButton');
const useVerifyImageFileInput = document.getElementById('useVerifyImageFileInput');
const verifyImageRow = document.getElementById('verifyImageRow');
const feedbackContainer = document.getElementById('feedbackContainer');
var sessionToken = "";
var sessionId = "";

async function getRandomId() {
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

async function getDummyDeviceId() {
  let deviceId = (await navigator.mediaDevices.enumerateDevices()).find(
    (device) => device.deviceId !== ''
  )?.deviceId;

  if (deviceId === null || deviceId === '') {
    deviceId = getRandomId();
  }

  return deviceId;
};

async function getDummyUserId() {
  return getRandomId();
}

// This method shows how to start face liveness check.
async function startFaceLiveness(event) {
  let action = "detectLiveness";
  let sessionCreationHeaders = { 'Content-Type': 'application/json' };
  feedbackContainer.setAttribute('hidden', true);
  feedbackContainer.style.display = "none";

  if (event.target.dataset.mode != "Retry") {
    splash.setAttribute('hidden', null);
    startPassiveButton.toggleAttribute("hidden");
    startPassiveActiveButton.toggleAttribute("hidden");
    verifyImageRow.toggleAttribute("hidden");
    
    // Step 2: Obtain session-authorization-token.
    // Disclaimer: This is just an illustration of what information is used to create a liveness session using a mocked backend. For more information on how to orchestrate the liveness solution, please refer to https://aka.ms/azure-ai-vision-face-liveness-tutorial
    // In your production environment, you should perform this step on your app-backend and pass down the session-authorization-token down to the frontend.

    // Note: The liveness-operation-mode is retrieved from 'data-mode' attribute of the start buttons, for more information: https://aka.ms/face-api-reference-livenessoperationmode
    const livenessOperationMode = event.target.dataset.mode;
    let deviceId = await getDummyDeviceId();
    let userId = await getDummyUserId();
    
    // Note1: More information regarding each request parameter involved in creating a liveness session is here: https://aka.ms/face-api-reference-createlivenesssession
    const sessionBodyStruct = { 
      livenessOperationMode: livenessOperationMode,
      deviceCorrelationId: deviceId,
      userCorrelationId: userId,
      enableSessionImage:true 
    };
    let sessionCreationBody = JSON.stringify(sessionBodyStruct);
    
    
    // Note2: You can also create a liveness session with verification by attaching a verify image during session-create, reference: https://aka.ms/face-api-reference-createlivenesswithverifysession
    if (useVerifyImageFileInput.files.length > 0) {
      sessionCreationBody = new FormData();
      sessionCreationBody.append("verifyImage", useVerifyImageFileInput.files[0], useVerifyImageFileInput.files[0].name);
      sessionCreationBody.append("livenessOperationMode", livenessOperationMode);
      sessionCreationBody.append("deviceCorrelationId", deviceId);
      sessionCreationBody.append("userCorrelationId", userId);
      sessionCreationBody.append("enableSessionImage", true);
      sessionCreationHeaders = {};
      action = "detectLivenessWithVerify";
    }
    // Calling a mocked app backend to create the session, this part is left to the developer to implement in a production setting. Code samples are provided in https://aka.ms/azure-ai-vision-face-liveness-tutorial
    const session = await (await fetch(`/api/${action}-sessions`, { method: "POST", headers: sessionCreationHeaders, body: sessionCreationBody, })).json();
    sessionToken = session.authToken;
    sessionId = session.sessionId;
  }
  else {
    continueButton.toggleAttribute("hidden");
    retryButton.toggleAttribute("hidden");

    var faceLivenessDetector = document.querySelector("azure-ai-vision-face-ui");
    if (faceLivenessDetector != null) {
      document.getElementById("container").removeChild(faceLivenessDetector);
    }
  }
  
  // Step 3: create the azure-ai-vision-face-ui element to process face liveness and attach it to DOM.
  var faceLivenessDetector = document.querySelector("azure-ai-vision-face-ui");
  if (faceLivenessDetector == null) {
    faceLivenessDetector = document.createElement("azure-ai-vision-face-ui");
    document.getElementById("container").appendChild(faceLivenessDetector);
  }
   
  // For multi-camera scenarios, you can set desired deviceId by using following APIs:
  // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method.
  // You can then set the desired deviceId as an attribute faceLivenessDetector.mediaInfoDeviceId = <desired-device-id>
  // Step 4: Start the face liveness check session and handle the promise returned appropriately.
  // Note: For added security, you are not required to trust the 'status' property from client.
  // Your backend can and should verify this by querying about the session Face API directly.
  faceLivenessDetector.start(sessionToken)
  .then(async resultData => {
    console.log(resultData);
    // Once the session is completed and promise fulfilled, you can query the result from your backend service by calling the sessions results API
    // https://aka.ms/face-api-reference-createlivenesssessionwithverifyimage
    const sessionResult = await (await fetch(`/api/${action}-sessions/${sessionId}`, { method: "GET", headers: sessionCreationHeaders })).json();

    const livenessStatus = sessionResult?.results?.attempts?.[0]?.result?.livenessDecision ?? sessionResult?.result?.response?.body?.livenessDecision ?? null;
    const livenessCondition = livenessStatus == "realface";
    const livenessIcon = livenessCondition ? checkmarkCircleIcon : dismissCircleIcon;
    const abuseDetected = sessionResult?.results?.attempts?.[0]?.result?.abuseMonitoringResult?.isAbuseDetected ;
    let livenessText = null;
    if (livenessCondition) {
      livenessText = "Real Person";
      if (abuseDetected != null && abuseDetected) {
        livenessText += " - Abuse Detected";
      }
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
    retryButton.toggleAttribute("hidden");
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
    retryButton.toggleAttribute("hidden");
    feedbackContainer.style.display = "flex";
    feedbackContainer.removeAttribute('hidden');
  });
}

function restartFaceLiveness() {
  // Step 5: Remove the face liveness detector element from DOM.
  var faceLivenessDetector = document.querySelector("azure-ai-vision-face-ui");
  if (faceLivenessDetector != null) {
    document.getElementById("container").removeChild(faceLivenessDetector);
  }
  splash.toggleAttribute('hidden');
  startPassiveButton.toggleAttribute("hidden");
  startPassiveActiveButton.toggleAttribute("hidden");
  verifyImageRow.toggleAttribute("hidden");
  continueButton.toggleAttribute("hidden");
  retryButton.toggleAttribute("hidden");
  feedbackContainer.setAttribute('hidden', true);
  feedbackContainer.style.display = "none";
}

// Step 6: Wire up face liveness check start method to the start buttons.
startPassiveButton.addEventListener('click', startFaceLiveness);
startPassiveActiveButton.addEventListener('click', startFaceLiveness);
continueButton.addEventListener('click', restartFaceLiveness);
retryButton.addEventListener('click', startFaceLiveness);