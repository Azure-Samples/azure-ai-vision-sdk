# Get started with the Azure AI Vision Face UI Web SDK

In this sample, you will learn how to build and run the face liveness detection application.

## Table of Contents

* [Introduction](#introduction)
* [Prerequisites](#prerequisites)
* [Step 1: Set up the environment](SetupEnvironment.md)
* [Step 2: Build and run sample app](#step-2-build-and-run-sample-app)
* [Step 3: Integrate face liveness detector into your own application](#step-3-integrate-face-liveness-detector-into-your-own-application)
    * [Obtaining a session token](#obtaining-a-session-token)
    * [Injecting the web component](#injecting-the-web-component)
    * [Deployment](#deployment)
* [Localization](#🌍-localization)
   * [Setting a Locale](#🌐-setting-a-locale)
* [UX Customization](#ux-customization)
   * [Increase your brightness image](#increase-your-brightness-image)
   * [Font size](#font-size)
   * [Font family](#font-family)
   * [Continue button](#continue-button)
   * [Feedback messages](#feedback-messages)
* [FAQ](#faq)
   * [Q: How can I get the results of the liveness session?](#q-how-can-i-get-the-results-of-the-liveness-session)
   * [Q: How can I automate deployment of the assets?](#q-how-can-i-automate-deployment-of-the-assets)

## Introduction

The Azure AI Vision Face UI Web SDK is a client library intended to enable the integration of the face liveness feature into web-applications. It works seamlessly with Azure AI Face APIs to determine the authenticity of a face in a video stream.

* API reference: [AzureAIVisionFaceUI](https://aka.ms/azure-ai-vision-face-liveness-client-sdk-web-api-reference)

## Prerequisites

1. An Azure Face API resource subscription.
2. Install node from https://nodejs.org/en/download/prebuilt-installer

## Step 2: Build and run sample app

Follow these steps to quickly run a sample app built with Next.js, Angular, or Vue.js.

1. Follow the steps in [SetupEnvironment.md](SetupEnvironment.md) to install the npm package.

2. Copy `facelivenessdetector-assets/` folder from `node_modules/azure-ai-vision-face-ui` to `public/`.

3. Update the variables in `.env.local` with your own face-api key and endpoint.

4. Run the app with `npm run dev`. On the first run, the development server may take a few minutes to initialize.

Note: the [`samples/web/javascript`](./javascript/) contains a fully featured vanilla-javascript sample

## Step 3: Integrate face liveness detector into your own application

First run through the steps in the [SetupEnvironment.md](./SetupEnvironment.md) section to install the npm package.

### Obtaining a session token

  The session-authorization-token is required to start a liveness session. See `fetchTokenOnServer` in `server.js` file method for a demo. 
  For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial 

### Injecting the web component

  After obtaining a valid session-authorization-token, you can integrate the web component, `<azure-ai-vision-face-ui> `element, using JavaScript.

  ```javascript
  const azureAIVisionFaceUI = document.createElement("azure-ai-vision-face-ui");
  document.getElementById("your-container-id").appendChild(azureAIVisionFaceUI);
  azureAIVisionFaceUI.start("***FACE_API_SESSION_TOKEN***")
    .then(resultData => { 
      // The resultData which is LivenessDetectionSuccess interface.
      // The result of analysis is queryable from the service using sessions result API
      // https://learn.microsoft.com/rest/api/face/liveness-session-operations/get-liveness-session-result?view=rest-face-v1.2-preview.1&tabs=HTTP

    })
    .catch(errorData => {
      // In case of failures, the promise is rejected. The errorData which is LivenessDetectionError interface, contains the reason for the failure.
    });
  ```

### Deployment

  It's important to note that essential assets like WebAssembly (wasm) files and localization files are packaged within the NPM distribution. During deployment to a production environment, it's essential to include these assets. As an example, you can deploy the 'facelivenessdetector-assets' from the node_modules\azure-ai-vision-face-ui folder to the root assets directory like `public` folder after the npm installation to ensure proper asset deployment.


## 🌍 Localization

The Azure AI Vision Face UI SDK embraces global diversity by supporting multiple languages. The complete list of supported locales and language dictionary is available [here](../Localization.md)

### 🌐 Setting a Locale

  To use a specific locale, assign the locale attribute to the azure-ai-vision-face-ui component. If translations are available for that locale, they will be used; otherwise, the SDK will default to English.

* Example - Enabling Portuguese
  ```javascript
  const azureAIVisionFaceUI = document.createElement("azure-ai-vision-face-ui");
  azureAIVisionFaceUI.locale = "pt-PT";  // Setting Portuguese locale
  document.getElementById("your-container-id").appendChild(azureAIVisionFaceUI);
  ```

## UX Customization
You can customize the layout of the page using following options:

### Increase your brightness image
  Customize the default "Increase your screen brightness" image by providing your own image. Ensure the image is correctly deployed for production.
  azureAIVisionFaceUI.brightnessImagePath = newImagePath;
  
### Font size
  Customize the default font size for all the text. The default is 1.5rem
  azureAIVisionFaceUI.fontSize = newSize;
    
### Font family
  Customize the default font family for all the text. The default value is `font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;`
  
  azureAIVisionFaceUI.fontFamily = newFontFamily;
    
### Continue button
  Customize the look and feel of the "Continue" button by providing your own CSS styles. To change the text, use `languageDictionary` attribute and override the "Continue" key.
  
  azureAIVisionFaceUI.continueButtonStyles = newCSS;
    
### Feedback messages
  Customize the look and feel of the feedback messages by providing your own CSS styles.
  
  azureAIVisionFaceUI.feedbackMessageStyles = newCSS;
    
## FAQ
    
### Q: How can I get the results of the liveness session?

Once the session is completed and the promise fulfilled, for security reasons the client does not receive the outcome whether face is live or spoof. 

You can query the result from your backend service by calling the sessions results API to get the outcome
https://aka.ms/face/liveness-session/get-liveness-session-result

### Q: How can I automate deployment of the assets?

* React

  For deployment
  You can add postbuild script to your package.json to copy facelivenessdetector-assets to public
  ```
  "scripts": {
      "postbuild": "cpy node_modules/azure-ai-vision-face-ui/facelivenessdetector-assets/**/* public/facelivenessdetector-assets --parents"
    }
  ```

* Angular

  Please see the [AngularJS integration example](./angularjs/src/face/face.component.ts) at `samples/angularjs/src/face/face.component.ts`

  For deployment you can add section to deploy facelivenessdetector-assets in your projects' build section of the configuration file
  ```
  
  "build": {
    "options": {
      "assets": [
        { "glob": "**/*", "input": "./node_modules/azure-ai-vision-face-ui/facelivenessdetector-assets", "output": "/facelivenessdetector-assets" }
      ],
    }
  }
  ```
