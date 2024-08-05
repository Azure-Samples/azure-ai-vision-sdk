<script setup lang="ts">
/* NOTE: This is an EXAMPLE app page for controlling the state of the app. This is not required to use the web component.
          For integration example, please see face-vuejs/src/components/AnalyzerView.vue */

import { ref } from "vue";
import InitialView from "./components/InitialView.vue";
import AnalyzerView from "./components/AnalyzerView.vue";
import ResultView from "./components/ResultView.vue";
import RetryView from "./components/RetryView.vue";
import type { FaceAnalyzedResult } from "azure-ai-vision-faceanalyzer";

type LivenessOperationMode = "Passive" | "PassiveActive";

const verifyImage = ref<File | undefined>(undefined);
const action = ref<"detectLiveness" | "detectLivenessWithVerify">(
  "detectLiveness"
);
const analyzerState = ref<"Initial" | "Analyzer" | "Retry" | "Result">(
  "Initial"
);
const livenessOperationMode = ref<LivenessOperationMode>("PassiveActive");
const faceAnalyzedResult = ref<FaceAnalyzedResult>();
const recognitionCondition = ref<boolean | undefined>();
const recognitionText = ref<string | undefined>();
const livenessCondition = ref<boolean>(false);
const livenessText = ref<string>("");
const errorMessage = ref<string>("");

function initFaceAnalyzer(event: {
  file: File | undefined;
  livenessOperationMode: LivenessOperationMode;
}) {
  verifyImage.value = event.file;
  action.value = event.file ? "detectLivenessWithVerify" : "detectLiveness";
  livenessOperationMode.value = event.livenessOperationMode;
  analyzerState.value = "Analyzer";
}

function displayResult(event: {
  faceAnalyzedResult: FaceAnalyzedResult;
  recognitionCondition: boolean | undefined;
  recognitionText: string | undefined;
  livenessCondition: boolean;
  livenessText: string;
}) {
  faceAnalyzedResult.value = event.faceAnalyzedResult;
  recognitionCondition.value = event.recognitionCondition;
  recognitionText.value = event.recognitionText;
  livenessCondition.value = event.livenessCondition;
  livenessText.value = event.livenessText;
  analyzerState.value = "Result";
}

function fetchFailureCallback(error: string) {
  errorMessage.value = `Failed to fetch the token. ${error}`;
  analyzerState.value = "Retry";
}

function continueFaceAnalyzer() {
  analyzerState.value = "Initial";
}
</script>

<template>
  <InitialView
    v-if="analyzerState === 'Initial'"
    @initFaceAnalyzer="initFaceAnalyzer"
  />
  <AnalyzerView
    v-if="analyzerState === 'Analyzer'"
    :liveness-operation-mode="livenessOperationMode"
    :action="action"
    :file="verifyImage"
    :send-results-to-client="true"
    @display-result="displayResult"
    @retry-analyzer="fetchFailureCallback"
  />
  <ResultView
    v-if="analyzerState === 'Result'"
    :liveness-text="livenessText"
    :liveness-condition="livenessCondition"
    :recognition-text="recognitionText"
    :recognition-condition="recognitionCondition"
    @continue="continueFaceAnalyzer"
  />
  <RetryView
    v-if="analyzerState === 'Retry'"
    :error="errorMessage"
    @retry="continueFaceAnalyzer"
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
