<script setup lang="ts">
/* NOTE: This is an EXAMPLE retry page for displaying errors from generating face liveness session. To see how to use the SDK in your application please see face-vuejs/src/components/AnalyzerView.vue */

import { ref, defineProps } from "vue";

const props = defineProps(["error"]);

const errorMessage = ref<string>(props.error);
</script>

<template>
  <div class="feedback">
    <div class="item">
      <span>{{ errorMessage }}</span>
    </div>
    <div class="item">
      <fluent-button
        class="ms-fontSize-l"
        appearance="accent"
        id="continueButton"
        @click="$emit('retry')"
        >Retry</fluent-button
      >
    </div>
  </div>
</template>

<style scoped>
@import "../assets/page_styles.css";

.feedback-column {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  align-items: center;
}

.feedback {
  --height: 30px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
  padding: 2em 0;
  position: absolute;
  background: white;
  width: 100%;
  place-content: center;
  row-gap: 18vh;
}

.feedback .item {
  font-weight: 500;
  font-size: 1.2em;
  padding: 5px;
  width: max-content;
  display: flex;
  position: relative;
  justify-content: center;
}

.feedback .item img {
  width: var(--height);
  height: var(--height);
}

.feedback .item span {
  line-height: var(--height);
  padding: 0 10px;
  display: inline-block;
}
</style>
