import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "node:path";

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          // Treat azure-ai-vision-face-ui and fluent-* as custom elements
          isCustomElement: (tag) =>
            tag.startsWith("azure-ai-vision-") || tag.startsWith("fluent-"),
        },
      },
    }),
  ],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
    },
  },
});
