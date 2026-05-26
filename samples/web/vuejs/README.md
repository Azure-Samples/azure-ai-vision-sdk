# Face Liveness Detector - Vue.js Sample

A Vue 3 sample app demonstrating the Azure AI Vision Face UI Web SDK for face liveness detection, built with [Vite](https://vite.dev/).

## Prerequisites

- Node.js 18+
- An Azure Face API resource (endpoint and key)

## Setup

1. Install dependencies:

    ```sh
    npm install
    ```

2. Create a symlink for the Face SDK assets (required for WASM modules):

    ```sh
    ln -s ../node_modules/@azure-ai-vision-face/ui-assets/facelivenessdetector-assets public/facelivenessdetector-assets
    ```

    Note: On Windows, use `mklink /D public\facelivenessdetector-assets ..\node_modules\@azure-ai-vision-face\ui-assets\facelivenessdetector-assets` instead of `ln -s`.

3. Copy `.env.local.example` or create `.env.local` with your Azure Face API credentials:

    ```
    FACE_ENDPOINT="https://<your-resource>.cognitiveservices.azure.com/"
    FACE_KEY="<your-api-key>"
    ```

    > **Important:** `.env.local` contains secrets and must not be checked into source control.

## Run

```sh
npm run dev
```

This builds the app with Vite and starts the Express server at `http://localhost:3000`.

Other scripts:
- `npm run build` — production build only
- `npm run serve` — Vite dev server (frontend only, no backend API)
- `npm start` — Express server only (requires prior build)

## Project Structure

- `server.js` — Express backend for token generation and session results
- `src/` — Vue 3 app source (Composition API with `<script setup>`)
- `public/` — static assets (images, splash page, Face SDK WASM assets)
- `vite.config.ts` — Vite configuration
- `index.html` — app entry point (Vite convention, lives at project root)

For more details, see the [parent README](../README.md).