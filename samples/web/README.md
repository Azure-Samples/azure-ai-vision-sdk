# Get started with the Azure AI Vision Face UI Web SDK

In this sample, you will learn how to build and run the face liveness detection application.

## Table of Contents

* [Introduction](#introduction)
* [Prerequisites](#prerequisites)
* [Step 1: Installation](#step-1-installation)
* [Step 2: Build and run sample app](#step-2-build-and-run-sample-app)

## Introduction

The Azure AI Vision Face UI Web SDK is a client library intended to enable the integration of the face liveness feature into web-applications. It works seamlessly with Azure AI Face APIs to determine the authenticity of a face in a video stream.

* API reference: [FaceLivenessDetector](https://aka.ms/azure-ai-vision-face-liveness-client-sdk-web-api-reference-modules)

## Prerequisites

1. An Azure Face API resource subscription.
2. Install node from https://nodejs.org/en/download/prebuilt-installer

## Step 1: Installation

## Installation

1. Create .npmrc file in root of app folder to pull packages from `https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/` registry. 
An example .npmrc file is available here(https://github.com/Azure-Samples/azure-ai-vision-sdk/blob/main/samples/web/angularjs/.npmrc).

2. Fetch the base64 access token required in the .npmrc file using the API: [Liveness Session Operations - Get Client Assets Access Token](https://learn.microsoft.com/rest/api/face/liveness-session-operations/get-client-assets-access-token?view=rest-face-v1.3-preview)

3. To install the SDK via NPM, run the following command in the root of the app folder:

    ```sh
    npm install @azure/ai-vision-face-ui@latest
    ```

## Step 2: Build and run sample app

Follow these steps to quickly run a sample app built with Next.js, Angular, or Vue.js.

1. Follow the steps in `Installation` section to install the npm package.

2. Create a symlink from `public/facelivenessdetector-assets` to `node_modules/@azure-ai-vision-face/ui-assets/facelivenessdetector-assets`:

    ```sh
    ln -s ../node_modules/@azure-ai-vision-face/ui-assets/facelivenessdetector-assets public/facelivenessdetector-assets
    ```

    Note: On Windows, use `mklink /D` instead of `ln -s`.

3. Update the variables in `.env.local` with your own face-api key and endpoint.

4. Run the app with `npm run dev`. On the first run, the development server may take a few minutes to initialize.

Note: the [`samples/web/javascript`](https://github.com/Azure-Samples/azure-ai-vision-sdk/tree/main/samples/web/javascript/) contains a fully featured vanilla-javascript sample

### Web component and runtime assets

The Web SDK installation contains two browser-side packages:

* `@azure/ai-vision-face-ui` provides the public
  `<azure-ai-vision-face-ui>` component and its type definitions.
* `@azure-ai-vision-face/ui-assets` provides the gated runtime assets,
  including localization, JavaScript loaders, and WebAssembly binaries
  under `facelivenessdetector-assets/`.

Deploy the complete `facelivenessdetector-assets` directory at the site
root. The component detects WebAssembly SIMD support and selects the
SIMD runtime when available, with a non-SIMD runtime for compatible
fallback. Copying only one loader or `.wasm` file can therefore break
the experience on other browsers.

Gated distribution does not make browser assets secret: JavaScript and
WASM downloaded by a browser can be inspected. Never expose the Face
API key in frontend code, and retrieve the liveness decision through
your app server. Following the
[Face Liveness architecture](https://learn.microsoft.com/azure/ai-services/face/concept-face-liveness-detection#how-it-works),
the server creates a short-lived session and sends its authorization
token to the frontend; the frontend runs the SDK, while the server
retrieves the live/spoof result.

### Retrying and running inside an iframe

A session token represents a single liveness session. You can let a user retry within that session without requesting a new token, as long as you do not reload the page: remove the `<azure-ai-vision-face-ui>` element, create a new one, and call `start()` again with the same token.

Reloading the page starts the flow over and needs a token from a new session. This is true whether the SDK runs at the top level or inside an iframe, so a retry that reloads the iframe (re-setting its `src`, navigating it, or calling `location.reload()` inside it) needs a new token. To retry inside an iframe without a new token, keep the iframe loaded and re-create the component in place (for example by having the parent page message the iframe to retry).

The vanilla-javascript sample includes a parent + iframe page that demonstrates both approaches side by side. See the "Retrying a liveness check" and "Using the SDK inside an iframe" sections of the [SDK README](https://www.npmjs.com/package/@azure/ai-vision-face-ui) for code and details.
