# Get started with the Azure AI Vision Face UI Web SDK (Blazor WebAssembly)

In this sample, you will learn how to build and run the face liveness detection application using Blazor WebAssembly (.NET 8).

## Table of Contents

* [Introduction](#introduction)
* [Prerequisites](#prerequisites)
* [Step 1: Installation](#step-1-installation)
* [Step 2: Configuration](#step-2-configuration)
* [Step 3: Build and run sample app](#step-3-build-and-run-sample-app)
* [Architecture notes](#architecture-notes)

## Introduction

The Azure AI Vision Face UI Web SDK is a client library intended to enable the integration of the face liveness feature into web-applications. It works seamlessly with Azure AI Face APIs to determine the authenticity of a face in a video stream.

This sample demonstrates how to integrate the SDK into a **Blazor WebAssembly** application hosted by an ASP.NET Core backend. The backend handles session token generation and result retrieval, keeping API keys server-side.

* API reference: [FaceLivenessDetector](https://aka.ms/azure-ai-vision-face-liveness-client-sdk-web-api-reference-modules)

## Prerequisites

1. An Azure Face API resource subscription.
2. [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0) or later.
3. [Node.js](https://nodejs.org/en/download/prebuilt-installer) (for npm package installation).

## Step 1: Installation

1. Update the `.npmrc` file in the `FaceLivenessDetectorSample.Client/` folder with your npm registry credentials. Fetch the base64 access token using the API: [Liveness Session Operations - Get Client Assets Access Token](https://learn.microsoft.com/rest/api/face/liveness-session-operations/get-client-assets-access-token?view=rest-face-v1.3-preview)

2. Install the npm package:

    ```sh
    cd FaceLivenessDetectorSample.Client
    npm install
    ```

    This installs `@azure/ai-vision-face-ui`. The MSBuild targets in the `.csproj` automatically copy the SDK assets to `wwwroot/` during build.

## Step 2: Configuration

Update `FaceLivenessDetectorSample.Server/appsettings.json` with your Azure Face API credentials:

```json
{
  "FaceApi": {
    "Endpoint": "https://<your-resource>.cognitiveservices.azure.com/",
    "Key": "<your-face-api-key>"
  }
}
```

## Step 3: Build and run sample app

1. Build the solution:

    ```sh
    dotnet build
    ```

2. Run the server (which also serves the Blazor WASM client):

    ```sh
    dotnet run --project FaceLivenessDetectorSample.Server
    ```

3. Open the URL shown in the console (e.g., `https://localhost:7165`) in your browser.

4. Click **Start Passive** or **Start PassiveActive** to begin liveness detection.

## Architecture notes

### Dual WASM runtime isolation

Blazor WebAssembly runs .NET code via an Emscripten-compiled WASM runtime. The Face SDK also uses an Emscripten-compiled WASM module. Loading both in the same page causes global symbol collisions (e.g., `addRunDependency`).

To solve this, the Face SDK runs inside an isolated same-origin iframe (`face-detector.html`), giving it a separate JavaScript global scope. The Blazor app communicates with the iframe via `postMessage`.

### COEP/COOP headers

The Face SDK requires `SharedArrayBuffer`, which requires cross-origin isolation. The server sets these headers on all responses:

- `Cross-Origin-Embedder-Policy: require-corp`
- `Cross-Origin-Opener-Policy: same-origin`

The splash screen iframe uses the `credentialless` attribute to opt out of COEP enforcement for its cross-origin dependencies.

### Project structure

| Project | Role |
|---------|------|
| **Client** (Blazor WASM) | UI with JS interop to the SDK web component |
| **Server** (ASP.NET Core) | Backend API endpoints + hosts the Blazor WASM client |
| **Shared** | DTOs shared between Client and Server |
