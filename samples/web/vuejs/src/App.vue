<script setup lang="ts">
/* NOTE: This is an EXAMPLE app page for controlling the state of the app. This is not required to use the web component.
          For integration example, please see face-vuejs/src/components/LivenessDetectorView.vue */

import { ref } from "vue";
import InitialView from "./components/InitialView.vue";
import LivenessDetectorView from "./components/LivenessDetectorView.vue";
import ResultView from "./components/ResultView.vue";
import RetryView from "./components/RetryView.vue";

type LivenessOperationMode = "Passive" | "PassiveActive";

const verifyImage = ref<File | undefined>(undefined);
const action = ref<"detectLiveness" | "detectLivenessWithVerify">(
  "detectLiveness"
);
const livenessDetectorState = ref<"Initial" | "LivenessDetector" | "Retry" | "Result">(
  "Initial"
);
const livenessOperationMode = ref<LivenessOperationMode>("PassiveActive");
const recognitionCondition = ref<boolean | undefined>();
const recognitionText = ref<string | undefined>();
const livenessCondition = ref<boolean>(false);
const livenessText = ref<string>("");
const errorMessage = ref<string>("");

function initFaceLivenessDetector(event: {
  file: File | undefined;
  livenessOperationMode: LivenessOperationMode;
}) {
  verifyImage.value = event.file;
  action.value = event.file ? "detectLivenessWithVerify" : "detectLiveness";
  livenessOperationMode.value = event.livenessOperationMode;
  livenessDetectorState.value = "LivenessDetector";
}

function displayResult(event: {
  recognitionCondition: boolean | undefined;
  recognitionText: string | undefined;
  livenessCondition: boolean;
  livenessText: string;
}) {
  recognitionCondition.value = event.recognitionCondition;
  recognitionText.value = event.recognitionText;
  livenessCondition.value = event.livenessCondition;
  livenessText.value = event.livenessText;
  livenessDetectorState.value = "Result";
}

function fetchFailureCallback(error: string) {
  errorMessage.value = `Failed to fetch the token. ${error}`;
  livenessDetectorState.value = "Retry";
}

function continueFaceLivenessDetector() {
  livenessDetectorState.value = "Initial";
}
</script>

<template>
  <InitialView
    v-if="livenessDetectorState === 'Initial'"
    @initFaceLivenessDetector="initFaceLivenessDetector"
  />
  <LivenessDetectorView
    v-if="livenessDetectorState === 'LivenessDetector'"
    :liveness-operation-mode="livenessOperationMode"
    :action="action"
    :file="verifyImage"
    :send-results-to-client="true"
    @display-result="displayResult"
    @retry-liveness-detector="fetchFailureCallback"
  />
  <ResultView
    v-if="livenessDetectorState === 'Result'"
    :liveness-text="livenessText"
    :liveness-condition="livenessCondition"
    :recognition-text="recognitionText"
    :recognition-condition="recognitionCondition"
    @continue="continueFaceLivenessDetector"
  />
  <RetryView
    v-if="livenessDetectorState === 'Retry'"
    :error="errorMessage"
    @retry="continueFaceLivenessDetector"
  />
</template>

<style>
@import "../public/styles/fabric-9.6.1.min.css";

.page {
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    Oxygen, Ubuntu, Cantarell, "Open Sans", "Helvetica Neue", sans-serif;
  min-width: 100%;
  margin: 0;
  overflow: hidden;
  overflow-y: auto;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

body {
  margin: 0;
}
</style>
