<script setup lang="ts">

// Step 1: Import the web component.
import "@azure/ai-vision-face-ui";
import { FaceLivenessDetector, LivenessDetectionError } from '@azure/ai-vision-face-ui';

import { ref, watchEffect, defineProps, defineEmits } from "vue";
import { SessionAuthorizationData, fetchTokenFromAPI, fetchSessionResultFromAPI } from "../utils/utils";


const props = defineProps([
  "livenessOperationMode",
  "action",
  "file",
]);

const containerRef = ref<HTMLDivElement | null>(null);
const faceLivenessDetector = ref<FaceLivenessDetector | null>(
  null
);
const emit = defineEmits(["retryLivenessDetector", "displayResult"]);

// We fetch the access token from our backend on component load.
watchEffect(async () => {
  const fetchResult = await fetchTokenFromAPI(
    props.livenessOperationMode,
    props.file
  );

  if (fetchResult.sessionAuthData) {
    startFaceLiveness(fetchResult.sessionAuthData, props.file);
  } else {
    emit("retryLivenessDetector", fetchResult.message);
  }
});

// This method shows how to start face liveness check.
async function startFaceLiveness(sessionData: SessionAuthorizationData, file: string) {
  let action = (file !== undefined) ? "detectLivenessWithVerify": "detectLiveness";

  // Step 2: query the azure-ai-vision-face-ui element to process face liveness.
  // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
  // An example would be to retry in case of a failure.
  faceLivenessDetector.value = document.querySelector(
    "azure-ai-vision-face-ui"
  );

  if (faceLivenessDetector.value == null) {
    // Step 3: Create the faceLivenessDetector element and add it to DOM.
    faceLivenessDetector.value = document.createElement(
      "azure-ai-vision-face-ui"
    ) as FaceLivenessDetector;
    containerRef.value?.appendChild(faceLivenessDetector.value);
  }

  // For multi-camera scenarios, you can set desired deviceId by using following APIs
  // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
  // You can then set the desired deviceId as an attribute faceLivenessDetector.mediaInfoDeviceId = <desired-device-id>

  // Step 4: Start the face liveness check session and handle the promise returned appropriately.
  faceLivenessDetector.value.start(sessionData.authToken)
  .then(async resultData => {
    console.log(resultData);
      
    // Once the session is completed and promise fulfilled, the client does not receive the outcome whether face is live or spoof.
    // You can query the result from your backend service by calling the sessions results API
    // https://aka.ms/face/liveness-session/get-liveness-session-result
    var sessionResult = await fetchSessionResultFromAPI(action, sessionData.sessionId);
  
    // Set Liveness Status Results
    const livenessStatus = sessionResult.results.attempts[0].result.livenessDecision;
    const livenessCondition = livenessStatus == "realface";

    let livenessText = "";
    if (livenessCondition) {
      livenessText = 'Real Person';
    } else {
      livenessText = 'Spoof';
    }

    // Set Recognition Status Results (if applicable)
    let recognitionCondition;
    let recognitionText;
    if (action === "detectLivenessWithVerify") {
      recognitionCondition = sessionResult.results.attempts[0].result.verifyResult.isIdentical;

      if (recognitionCondition) {
        recognitionText = 'Same Person';
      } else {
        recognitionText = 'Not the same person';
      }
    }

    emit("displayResult", {
      recognitionCondition,
      recognitionText,
      livenessCondition,
      livenessText,
    });
  })
  .catch(error => {
    const errorData = error as LivenessDetectionError;
    let livenessText = errorData.livenessError as string;
    const livenessCondition = false;
    let recognitionText = undefined;
    let recognitionCondition = undefined;
    if (action == "detectLivenessWithVerify") {
      recognitionText = errorData.recognitionError;
      recognitionCondition = false
    }

    emit("displayResult", {
      recognitionCondition,
      recognitionText,
      livenessCondition,
      livenessText,
    });
  });
}
</script>

<template>
  <div id="liveness-detector-page">
    <div id="container" ref="containerRef"></div>
  </div>
</template>

<style scoped>
div#liveness-detector-page {
    padding: "0 20px";
    font-size: "14px";
}
</style>
