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

2. Copy `facelivenessdetector-assets/` folder from `node_modules/@azure-ai-vision-face/ui-assets` to `public/`.

3. Update the variables in `.env.local` with your own face-api key and endpoint.

4. Run the app with `npm run dev`. On the first run, the development server may take a few minutes to initialize.

Note: the [`samples/web/javascript`](https://github.com/Azure-Samples/azure-ai-vision-sdk/tree/main/samples/web/javascript/) contains a fully featured vanilla-javascript sample
