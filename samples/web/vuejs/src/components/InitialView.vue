<script setup lang="ts">
/* NOTE: This is an EXAMPLE splash page for starting the face liveness session. 
To see how to use the SDK in your application please see face-vuejs/src/components/LivenessDetectorView.vue */

import { ref } from "vue";

const verifyImage = ref<File | undefined>(undefined);
const verifyImageUrl = ref("");

function onFileChange(e: Event) {
  const elem = e.target as HTMLInputElement;
  if (elem.files) {
    verifyImageUrl.value = URL.createObjectURL(elem.files[0]);
    verifyImage.value = elem.files[0];
  } else {
    verifyImage.value = undefined;
  }
}
</script>

<template>
  <div class="page ms-Fabric">
    <iframe id="splash" title="splash" src="splash.html" role="status"></iframe>
    <div class="feedback" id="feedbackContainer" role="status" hidden></div>
    <div id="container"></div>
    <div class="row" id="verifyImageRow">
      <label class="verify-input">
        <input
          type="file"
          id="useVerifyImageFileInput"
          accept="image/*"
          @change="onFileChange"
        />
        Select Verify Image
      </label>
    </div>
    <img v-if="verifyImage" :src="verifyImageUrl" id="verify-image-preview" />
    <div class="row">
      <fluent-button
        class="ms-fontSize-l"
        appearance="accent"
        id="startPassiveButton"
        @click="
          $emit('initFaceLivenessDetector', {
            file: verifyImage,
            livenessOperationMode: 'Passive',
          })
        "
        >Start Passive</fluent-button
      >
      <fluent-button
        class="ms-fontSize-l"
        appearance="accent"
        id="startPassiveActiveButton"
        @click="
          $emit('initFaceLivenessDetector', {
            file: verifyImage,
            livenessOperationMode: 'PassiveActive',
          })
        "
        >Start PassiveActive</fluent-button
      >
    </div>
  </div>
</template>

<style scoped>
@import "../assets/page_styles.css";

img#verify-image-preview {
  max-width: 30vw;
  max-height: 80px;
  margin-left: auto;
  margin-right: auto;
}

input#useVerifyImageFileInput {
  display: none;
}

.verify-input {
  position: relative;
  color: white;
  background-color: #838383;
  display: flex;
  padding-left: 0.625rem;
  padding-right: 0.625rem;
  padding-top: 0.375rem;
  padding-bottom: 0.375rem;
  border-radius: 0.375rem;
  font-size: 0.875rem;
  line-height: 1.25rem;
  cursor: pointer;
}

.verify-input:hover {
  background-color: #9a9a9a;
}

@media screen and (max-width: 640px) {
  .verify-input {
    font-size: 1rem;
    line-height: 1.5rem;
  }
}

@media screen and (max-width: 767px) {
  div#verifyImageRow {
    flex-direction: column;
  }
}
</style>
