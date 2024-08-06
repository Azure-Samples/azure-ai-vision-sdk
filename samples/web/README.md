# Azure AI Vision FaceAnalyzer Web SDK

## Table of Contents

1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Get started](#get-started)
   1. [Build and run sample app in Next.js, Angular, or Vue.js](#build-and-run-sample-app-nextjs-angular-vuejs)
   2. [Integrate face analysis into your own application](#integrate-face-analysis-into-your-own-application)
      1. [Obtaining a session token](#obtaining-a-session-token)
      2. [Integration with JavaScript](#integration-with-javascript)
      3. [Integration with HTML](#integration-with-html)
      4. [Hosting environment updates](#hosting-environment-updates)
      5. [Deployment](#deployment)
4. [Set up the environment](SetupEnvironment.md)
5. [Framework specific integration notes](#framework-specific-integration-notes)
   1. [React or Next.js](#react)
   2. [Angular](#angular)
   3. [Vue.js](#vuejs)
6. [Localization Support](#🌍-localization-support)
   1. [Supported Languages](#📚-supported-languages)
   2. [Setting a Locale](#🌐-setting-a-locale)
      1. [Example - Enabling Portuguese](#example---enabling-portuguese)
   3. [Customizing Language Strings](#📝-customizing-language-strings)
      1. [Default Language Strings (English - en)](#default-language-strings-english---en)
      2. [Example - Custom Language Overrides](#example---custom-language-overrides)
   4. [Right-to-Left (RTL) Language Support](#↔️-right-to-left-rtl-language-support)
      1. [Example - Arabic RTL Support](#example---arabic-rtl-support)
7. [UX Customization](#ux-customization)
   1. [Increase your brightness image](#increase-your-brightness-image)
   2. [Font size](#font-size)
   3. [Font family](#font-family)
   4. [Continue button](#continue-button)
   5. [Feedback messages](#feedback-messages)

## Introduction

The Azure AI Vision FaceAnalyzer Web SDK is a client library intended to enable the integration of the face liveness feature into web-applications. It works seamlessly with Azure AI Face APIs to determine the authenticity of a face in a video stream.

## Prerequisites

1. An Azure Face API resource subscription.
2. Install node from https://nodejs.org/en/download/prebuilt-installer

## Get started

Depending on your scenario, you can choose from either one of the following scenarios:

- [Build and run sample app](#build-and-run-sample-app-nextjs-angular-vuejs) 
- [Integrate face analysis into your own application](#integrate-face-analysis-into-your-own-application)

### Build and run sample app (Next.js, Angular, Vue.js)

This is an illustration of how to quickly run a sample app built with Next.js, Angular, or Vue.js.

1. Follow the steps in [SetupEnvironment.md](./SetupEnvironment.md) to install the npm package.<br />
2. Copy `faceanalyzer-assets/` folder from `node_modules/azure-ai-vision-faceanalyzer` to `public/`.
3. Update the variables in `.env.local` with your own face-api key and endpoint.
4. Run the app with `npm run dev`. On the first run, the development server may take a few minutes to initialize. See `package.json` for other framework-specific commands.

Note: the [`samples/web/javascript`](./javascript/) contains a fully featured vanilla-javascript sample

### Integrate face analysis into your own application

First run through the steps in the [SetupEnvironment.md](./SetupEnvironment.md) section to install the npm package.

#### Obtaining a session token
A demo version on obtaining the token is in sample for the demo app to be built as an standalone solution, but this is not recommended. The session-authorization-token is required to start a liveness session. 
For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial 

#### Integration with JavaScript

After obtaining a valid API key for the Face API, you can integrate the web component <!--into your HTML or inject it dynamically--> using JavaScript. Use the API key to obtain a session token and provide this token to the web component.

The <azure-ai-vision-faceanalyzer> element can also be injected dynamically using JavaScript.

```javascript
const azureAIVisionFaceAnalyzer = document.createElement("azure-ai-vision-faceanalyzer");
azureAIVisionFaceAnalyzer.token = "***FACE_API_SESSION_TOKEN***";
customElements.upgrade(azureAIVisionFaceAnalyzer);
azureAIVisionFaceAnalyzer.addEventListener('analyzed', (event) => {
    // The event.result, which is FaceAnalyzedResult interface, contains the result of the analysis.
});
document.getElementById("your-container-id").appendChild(azureAIVisionFaceAnalyzer);
```

#### Integration with HTML

For rapid testing, you can initiate a liveness session by adding the <azure-ai-vision-faceanalyzer> HTML tag as shown below.

```html
<azure-ai-vision-faceanalyzer token="***FACE_API_SESSION_TOKEN***"></azure-ai-vision-faceanalyzer>
```

#### Hosting environment updates

This SDK uses WebAssembly, SharedArrayBuffer, and MediaDevices.getUserMedia() features from Web API. To host your web app, your hosting environment must support secure context. In particular, the response headers must include the following as baseline requirement:

 - `Content-Type` must be set properly according to the MIME type.
 - For top-level documents:
    - `Cross-Origin-Opener-Policy` must be set to `same-origin` .
    - `Cross-Origin-Embedder-Policy` must be set to `require-corp` (more widely supported) or `credentialless` (less widely supported).
 - For nested documents and subresources:
    - `Cross-Origin-Resource-Policy` must not be empty and must be set appropriately.

#### Deployment

It's important to note that essential assets like WebAssembly (wasm) files and worker JavaScript files are packaged within the NPM distribution. During deployment to a production environment, it's essential to include these assets. As an example, you can deploy the 'faceanalyzer-assets' from the node_modules\azure-ai-vision-faceanalyzer folder to the root assets directory after the npm installation to ensure proper asset deployment.

## Framework specific integration notes

### React
Please see the [Next.js integration example](./nextjs/face_analyzer/face.tsx) at `samples/nextjs/face_analyzer/face.tsx`

For deployment
You can add postbuild script to your package.json to copy faceanalyzer-assets to public
```
"scripts": {
    "postbuild": "cpy node_modules/azure-ai-vision-faceanalyzer/faceanalyzer-assets/**/* public/faceanalyzer-assets --parents"
  }
```

### Angular
Please see the [AngularJS integration example](./angularjs/src/face_analyzer/analyzer-page.component.ts) at `samples/angularjs/src/face_analyzer/analyzer-page.component.ts`

For deployment you can add section to deploy faceanalyzer-assets in your projects' build section of the configuration file
```
"projects": {
    "sample-project": {
      "projectType": "application",
      "root": "",
      "sourceRoot": "src",
      "prefix": "app",
      "architect": {
        "build": {
          "options": {
            "outputPath": "dist",
            "index": "src/index.html",
            "browser": "src/main.ts",
            "polyfills": ["zone.js"],
            "tsConfig": "tsconfig.app.json",
            "assets": [
              { "glob": "**/*", "input": "./node_modules/azure-ai-vision-faceanalyzer/faceanalyzer-assets", "output": "/faceanalyzer-assets" }
            ],
          }
        }
      }
    }
}
```

### Vue.js
Please see the [Vue.js integration example](./vuejs/src/components/AnalyzerView.vue) at `samples/vuejs/src/components/AnalyzerView.vue`

## 🌍 Localization Support

The Azure AI Vision FaceAnalyzer Web SDK embraces global diversity by supporting multiple languages, enabling you to provide a localized experience that enhances user interaction based on their language preferences.

### 📚 Supported Languages
By default, the SDK is set to English. However, you can customize it to support additional languages by providing locale-specific string dictionaries.
Currently, we have translations to the following languages:
- English (en)
- Portuguese (pt)
- Persian (fa)


### 🌐 Setting a Locale
To use a specific locale, assign the locale attribute to the azure-ai-vision-faceanalyzer component. If translations are available for that locale, they will be used; otherwise, the SDK will default to English.

#### Example - Enabling Portuguese
```javascript
const azureAIVisionFaceAnalyzer = document.createElement("azure-ai-vision-faceanalyzer");
azureAIVisionFaceAnalyzer.token = "***FACE_API_SESSION_TOKEN***";
azureAIVisionFaceAnalyzer.locale = "pt";  // Setting Portuguese locale
customElements.upgrade(azureAIVisionFaceAnalyzer);
document.getElementById("your-container-id").appendChild(azureAIVisionFaceAnalyzer);
```

### 📝 Customizing Language Strings
Override the SDK's default language strings by providing a JSON object containing your custom translations through the language attribute.

#### Default Language Strings (English - en)
Below is the complete list of default English strings used in the Azure AI Vision FaceAnalyzer Web SDK. These strings are used for various feedback messages and UI components within the SDK. You can override any of these strings by providing your own translations in the language attribute.
```json
{
    "None": "Hold still.",
    "LookAtCamera": "Look at camera.",
    "FaceNotCentered": "Center your face in the circle.",
    "MoveCloser": "Too far away! Move in closer.",
    "ContinueToMoveCloser": "Continue to move closer.",
    "MoveBack": "Too close! Move farther away.",
    "TooMuchMovement": "Too much movement.",
    "AttentionNotNeeded": "",
    "Smile": "Smile for the camera!",
    "LookInFront": "Look in front.",
    "LookUp": "Look up.",
    "LookUpRight": "Look up-right.",
    "LookUpLeft": "Look up-left.",
    "LookRight": "Look right.",
    "LookLeft": "Look left.",
    "LookDown": "Look down.",
    "LookDownRight": "Look down-right.",
    "LookDownLeft": "Look down-left.",
    "TimedOut": "Timed out.",
    "EnvironmentNotSupported": "Adjust lighting, refer to the tips.",
    "FaceTrackingFailed": "Face tracking failed.",
    "IncreaseBrightnessToMax": "Increase your screen brightness to maximum.",
    "Tip1Title": "Tip 1:",
    "Tip2Title": "Tip 2:",
    "Tip3Title": "Tip 3:",
    "Tip1": "Center your face in the preview. Make sure your eyes and mouth are visible, remove any obstructions like headphones.",
    "Tip2": "You may be asked to smile.",
    "Tip3": "You may be asked to move your nose towards the green color.",
    "Continue": "Continue"
}
```


#### Example - Custom Language Overrides
```javascript
const azureAIVisionFaceAnalyzer = document.createElement("azure-ai-vision-faceanalyzer");
azureAIVisionFaceAnalyzer.token = "***FACE_API_SESSION_TOKEN***";
const customLanguage = {
    "None": "Please hold still.",
    "Smile": "Please smile now!"
};
azureAIVisionFaceAnalyzer.language = customLanguage;
customElements.upgrade(azureAIVisionFaceAnalyzer);
document.getElementById("your-container-id").appendChild(azureAIVisionFaceAnalyzer);
```

### ↔️ Right-to-Left (RTL) Language Support
The SDK automatically adapts to right-to-left (RTL) languages, adjusting the UI components accordingly. Here is a list of supported RTL languages:

| ISO Language code | Language name |
| --- | --- |
| ar | Arabic |
| arc | Aramaic |
| dv | Divehi |
| fa | Persian |
| ha | Hausa |
| he | Hebrew |
| khw | Khowar |
| ks | Kashmiri |
| ku | Kurdish |
| ps | Pashto |
| ur | Urdu |
| yi | Yiddish |

##### Example - Arabic RTL Support
```javascript
const azureAIVisionFaceAnalyzer = document.createElement("azure-ai-vision-faceanalyzer");
azureAIVisionFaceAnalyzer.token = "***FACE_API_SESSION_TOKEN***";
azureAIVisionFaceAnalyzer.locale = "ar"; // Setting Arabic locale
azureAIVisionFaceAnalyzer.language = arStrings; // Custom Arabic strings
customElements.upgrade(azureAIVisionFaceAnalyzer);
document.getElementById("your-container-id").appendChild(azureAIVisionFaceAnalyzer);
```

## UX Customization
You can customize the layout of the page using following options:

### Increase your brightness image
Customize the default "Increase your screen brightness" image by providing your own image. Ensure the image is correctly deployed for production.
```   
   azureAIVisionFaceAnalyzer.brightnessImagePath = newImagePath;
```

### Font size
Customize the default font size for all the text. The default is 1.5rem
```
  azureAIVisionFaceAnalyzer.fontSize = newSize;
```

### Font family
Customize the default font family for all the text. The default value is `font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;`
```
  azureAIVisionFaceAnalyzer.fontFamily = newFontFamily;
```

### Continue button
Customize the look and feel of the "Continue" button by providing your own CSS styles. To change the text, use `language` attribute and override the "Continue" key.
```
  azureAIVisionFaceAnalyzer.continueButtonStyles = newCSS;
```

### Feedback messages
Customize the look and feel of the feedback messages by providing your own CSS styles.
```
  azureAIVisionFaceAnalyzer.feedbackMessageStyles = newCSS;
```
