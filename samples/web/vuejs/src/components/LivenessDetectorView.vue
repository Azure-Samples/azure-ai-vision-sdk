<script setup lang="ts">

// Step 1: Import the web component.
import "azure-ai-vision-face-ui";
import { FaceLivenessDetector, LivenessDetectionSuccess, LivenessDetectionError, LivenessStatus, RecognitionStatus } from 'azure-ai-vision-face-ui';

import { ref, watchEffect, defineProps, defineEmits } from "vue";
import { fetchTokenFromAPI } from "../utils/utils";


const props = defineProps([
  "livenessOperationMode",
  "action",
  "sendResultsToClient",
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
    props.sendResultsToClient,
    props.file
  );

  if (fetchResult.token) {
    startFaceLiveness(fetchResult.token, props.file);
  } else {
    emit("retryLivenessDetector", fetchResult.message);
  }
});

// This method shows how to start face liveness check.
async function startFaceLiveness(authToken: string, file: string) {
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
  // Note: For added security, you are not required to trust the 'status' property from client.
  // Your backend can and should verify this by querying about the session Face API directly.
  faceLivenessDetector.value.start(authToken)
  .then(resultData => {
    // Once the session is completed and promise fulfilled, the resultData contains the result of the analysis.
    // - livenessStatus: The result of the liveness detection.
    const faceLivenessDetectorResult = resultData as LivenessDetectionSuccess;
      
    // Set Liveness Status Results
    const livenessStatus = faceLivenessDetectorResult.livenessStatus;
    const livenessCondition = livenessStatus == LivenessStatus.RealFace;

    let livenessText = null;
    if (livenessStatus == LivenessStatus.RealFace) {
      livenessText = "Real Person";
    } else if (livenessStatus == LivenessStatus.SpoofFace) {
      livenessText = "Spoof Person";
    } else if (livenessStatus == LivenessStatus.ResultQueryableFromService) {
      livenessText = "ResultQueryableFromService";
    }

    // Set Recognition Status Results (if applicable)
    let recognitionCondition;
    let recognitionText;
    if (action == "detectLivenessWithVerify") {
      const recognitionStatus = faceLivenessDetectorResult.recognitionResult.status;
      recognitionCondition = recognitionStatus == RecognitionStatus.Recognized;

      if (recognitionStatus == RecognitionStatus.Recognized) {
        recognitionText = "Same Person";
      } else if (recognitionStatus == RecognitionStatus.NotRecognized) {
        recognitionText = "Not the same person";
      } else if (recognitionStatus == RecognitionStatus.ResultQueryableFromService) {
        recognitionText = "ResultQueryableFromService";
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
