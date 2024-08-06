<script setup lang="ts">
/* NOTE: This is an example of how to integrate the Face Analyzer Web Component with AngularJS.
          You can edit the callback function inside the "analyzed" event listener to change how the event is handled. */


// Step 1: Import the web component.
import "azure-ai-vision-faceanalyzer";
import type * as AzureAIVision from "azure-ai-vision-faceanalyzer";

import { ref, watchEffect, defineProps, defineEmits } from "vue";
import { fetchTokenFromAPI } from "../utils/utils";


const props = defineProps([
  "livenessOperationMode",
  "action",
  "sendResultsToClient",
  "file",
]);

const containerRef = ref<HTMLDivElement | null>(null);
const azureAIVisionFaceAnalyzer = ref<AzureAIVision.AzureAIFaceLiveness | null>(
  null
);
const faceAnalyzedResult = ref<AzureAIVision.FaceAnalyzedResult | undefined>();
const emit = defineEmits(["retryAnalyzer", "displayResult"]);

// We fetch the access token from our backend on component load.
watchEffect(async () => {
  const fetchResult = await fetchTokenFromAPI(
    props.livenessOperationMode,
    props.sendResultsToClient,
    props.file
  );

  if (fetchResult.token) {
    startFaceLiveness(fetchResult.token);
  } else {
    emit("retryAnalyzer", fetchResult.message);
  }
});

// This method shows how to start face liveness check.
async function startFaceLiveness(authToken: string) {
  // Step 2: query the azure-ai-vision-faceanalyzer element to process face liveness.
  // For scenarios where you want to use the same element to process multiple sessions, you can query the element once and store it in a variable.
  // An example would be to retry in case of a failure.
  azureAIVisionFaceAnalyzer.value = document.querySelector(
    "azure-ai-vision-faceanalyzer"
  );

  if (azureAIVisionFaceAnalyzer.value == null) {
    // Step 4: Create the faceanalyzer element, set the token and upgrade the element.
    azureAIVisionFaceAnalyzer.value = document.createElement(
      "azure-ai-vision-faceanalyzer"
    ) as AzureAIVision.AzureAIFaceLiveness;
    customElements.upgrade(azureAIVisionFaceAnalyzer.value);
    azureAIVisionFaceAnalyzer.value.token = authToken;

    // For multi-camera scenarios, you can set desired deviceId by using following APIs
    // You can enumerate available devices and filter cameras using navigator.mediaDevices.enumerateDevices method
    // Once you have list of camera devices you must call azureAIVisionFaceAnalyzer.filterToSupportedMediaInfoDevices() to get list of devices supported by AzureAIVisionFaceAnalyzer
    // You can then set the desired deviceId as an attribute azureAIVisionFaceAnalyzer.mediaInfoDeviceId = <desired-device-id>

    // Step 5: Handle analysis complete event
    // Note: For added security, you are not required to trust the 'status' property from client.
    // Your backend can and should verify this by querying about the session Face API directly.
    azureAIVisionFaceAnalyzer.value.addEventListener(
      "analyzed",
      (event: any) => {
        // console.log(event);
        // The event.result.livenessResult contains the result of the analysis.
        // The result contains the following properties:
        // - status: The result of the liveness detection.
        // - failureReason: The reason for the failure if the status is LivenessStatus.Failed.
        faceAnalyzedResult.value =
          event.detail as AzureAIVision.FaceAnalyzedResult;
        if (faceAnalyzedResult.value) {
          // Set Liveness Status Results
          const livenessStatus =
            azureAIVisionFaceAnalyzer.value!.LivenessStatus[
              faceAnalyzedResult.value.livenessResult.status
            ];
          const livenessCondition = livenessStatus == "Live";

          let livenessText = null;
          if (livenessStatus == "Live") {
            livenessText = "Live Person";
          } else if (livenessStatus == "Spoof") {
            livenessText = "Spoof";
          } else if (livenessStatus == "CompletedResultQueryableFromService") {
            livenessText = "CompletedResultQueryableFromService";
          } else {
            livenessText =
              azureAIVisionFaceAnalyzer.value!.LivenessFailureReason[
                faceAnalyzedResult.value.livenessResult.failureReason
              ];
          }

          // Set Recognition Status Results (if applicable)
          // For scenario that requires face verification, the event.detail.recognitionResult contains the result of the face verification.
          let recognitionCondition;
          let recognitionText;
          if (faceAnalyzedResult.value.recognitionResult.status > 0) {
            const recognitionStatus =
              azureAIVisionFaceAnalyzer.value!.RecognitionStatus[
                faceAnalyzedResult.value.recognitionResult.status
              ];
            recognitionCondition = recognitionStatus == "Recognized";

            if (recognitionStatus == "Recognized") {
              recognitionText = "Same Person";
            } else if (recognitionStatus == "NotRecognized") {
              recognitionText = "Not the same person";
            } else if (
              recognitionStatus == "CompletedResultQueryableFromService"
            ) {
              recognitionText = "CompletedResultQueryableFromService";
            } else {
              recognitionText =
              azureAIVisionFaceAnalyzer.value!.RecognitionFailureReason[
                  faceAnalyzedResult.value.recognitionResult.failureReason
                ];
            }
          }

          emit("displayResult", {
            faceAnalyzedResult,
            recognitionCondition,
            recognitionText,
            livenessCondition,
            livenessText,
          });
        }
      }
    );
    // Step 6: Add the element to the DOM.
    containerRef.value?.appendChild(azureAIVisionFaceAnalyzer.value);
  } else {
    // Step 7: In order to retry the session, in case of failure, you can use analyzeOnceAgain().
    // Make sure to set a new token for the next session.
    // For multi-camera scenarios, you need to set the deviceId again by following the aforementioned procedure
    // in Step 4
    azureAIVisionFaceAnalyzer.value.token = authToken;
    await azureAIVisionFaceAnalyzer.value.analyzeOnceAgain();
  }
}
</script>

<template>
  <div id="analyzer-page">
    <div id="container" ref="containerRef"></div>
  </div>
</template>

<style scoped>
div#analyzer-page {
    padding: "0 20px";
    font-size: "14px";
}
</style>
