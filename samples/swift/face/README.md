# Get started with the Azure AI Vision SDK for iOS (Preview)

In this sample, you will learn how to build and run the face liveness detection application. The Azure AI Vision SDK for iOS is currently in preview. The APIs are subject to change.

## Prerequisites

1. An Azure Face API resource subscription with a corresponding endpoint.
2. A Mac and iPhone (iOS 14 or above) to test the AzureAIVision SDK and cable to connect the phone to the Mac.
3. An Apple developer account is a requirement to install and run development apps on the iPhone.
4. The Mac with iOS development environment including Xcode 13+.
5. Get the API reference documentation. This will be included in the latest release artifacts here: https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases

## Set up the environment 
1. If this is your first time using your Mac to develop, you should build a sample app from [About Me — Sample Apps Tutorials | Apple Developer Documentation](https://developer.apple.com/tutorials/sample-apps/aboutme) and run it on your phone before you attempt to build the App here. This will help ensure that your developer environment has been setup properly.
2. To install the SDK, download the xcframework file from the latest release artifact here: https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases
    - You will need to get access to the SDK artifacts in order to run these samples. To get started you would need to apply for the [Face Recognition Limited Access Features](https://customervoice.microsoft.com/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR7en2Ais5pxKtso_Pz4b1_xUQjA5SkYzNDM4TkcwQzNEOE1NVEdKUUlRRCQlQCN0PWcu) to get access to the SDK artifacts. Please email [azureface@microsoft.com](azureface@microsoft.com) to get instructions on how to download the SDK. For more information, see the [Face Limited Access](https://learn.microsoft.com/en-us/legal/cognitive-services/computer-vision/limited-access-identity?context=%2Fazure%2Fcognitive-services%2Fcomputer-vision%2Fcontext%2Fcontext) page.

## Next steps
Now that you have setup your environment you can either:

- [Build and run sample app](#build-and-run-sample-app) 
- [Integrate face analysis into your own application](#integrate-face-analysis-into-your-own-application)

## Build and run sample App
1. Download the sample App folder and the required AzureAIVisionCore.xcframework, AzureAIVisionFace.xcframework in your Mac.
2. Unzip both the folders on your Mac. Double click the .xcodeproj file. This will open the project in Xcode.
3. Click the folder icon on the left in Xcode. This should bring up the project files. Select the AzureAIVisionFace and AzureAIVisionCore under Frameworks. Click (top right corner) the "Identity and Types" -> "Location", point it to the unzipped folder that contains the xcframework you downloaded and unzipped to.
4. Set the App bundle identifier and developer team in XCode per your needs.
5. Now attach your phone to the Mac. You should get prompt on the phone asking you to “Trust” the Mac. Enable the trust.
6. The phone should now show up in the Xcode top bar. Your iPhone name should be visible.
7. Now build and run the app.

### Run the sample
The first time the app runs, It is going to ask for camera permission. Allow the camera access. The App starts with the launch page. Some of the buttons are disabled until you configure the settings. Click the settings page. The settings page has the following fields/settings from top to bottom. Enter them correctly.

**API endpoint**
This is the Azure subscription endpoint, where the application makes the FaceAPI calls to.

**Subscription**
This secret key to access the Azure endpoint.

### Test out the key scenarios

The application supports 2 scenarios.

#### Liveness
This mode checks to see if the person in camera view is a live person or not.
1. Click on the "Liveness" button, following the guidance on the screen.
2. Click "Start" and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness analysis.
3. The result is displayed as Liveness status (Real/Spoof).
4. You can return to the main page by clicking "Continue".

#### LivenessWithVerify
This mode checks the person liveness with verification against a provided face image.
1. Click on the "LivenessWithVerify" button and it will prompt you to select an image of a face to verify against.
2. Click next and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness analysis.
3. The result is displayed as Liveness status (Real/Spoof), verification status (Recognized/NotRecognized), and verification confidence score.
4. You can return to the main page by clicking "Continue".

## Integrate face analysis into your own application
Based on the provided sample App, here is the instructions on how to integrate face analysis function into your own App. First, face analysis using AzureAIVision SDK requires a Vision source and a Vision service to be configured. The Vision source for the mobile scenario defaults to built in camera on the mobile device. The Vision source is wrapped into a SwiftUI View object that is required for a preview (please refer to sample App file "CameraPreviewView.swift"). The Vision service requires Azure subscription. The Azure subscription prerequisites are also common to all scenarios.
In the sample App, we provide sample views like: MainView.swift, LaunchView.swift, ResultView.swift, SettingsView.swift to help with the App flow. These files are not needed in your Application, and please take them for your reference only. More importantly, we provide the following example code to interact with the camera and the AzureAIVision SDK, which you should adapt into your own App properly.

1. **Configure your XCode project**

   (1) in the "Xcode -> Targets -> Build Settings -> Swift Compiler - Language", select the "C++ and Objective-C Interoperability" to be "C++ / Objective-C++

   (2) in the "Xcode -> Targets -> General -> Frameworks,Library,and Embedded Content", add the AzureAIVisionCore.xcframework, AzureAIVisionFace.xcframework, choose the option "Embed & Sign".

   (3) in the "Xcode -> Targets -> Info -> Custom iOS Target Properties", add Key "Privacy-Camera Usage Description" with your description, like "This App requires camera usage."

2. **Configure the camera view and preview**

   Copy the files: CameraPreviewView.swift, CameraView.swift, CameraView.swift, CameraRepresentable.swift into your project. "CameraView" is the view which will interact with the SDK to get all the feedbacks and results for users.

3. **Configure the liveness view**

   Copy the file LivenessView.swift into your project.
   "LivenessView" is the view your App should load for the face liveness analysis, and it will hold the "CameraView" for camera usage and interaction. (You may NOT need the EnvironmentObject like: pageSelection and sessionData in your App, but please handle the liveness messages and results, and App page switch in your own way.)

4. **Hook up the liveness view with the other views**

   Create other views per your needs, like what we have in the sample App (MainView.swift, LaunchView.swift, etc) to start/end the process, and save the face analysis results for your usage. 

5. **Configure the liveness algorithm details**

   Copy the file LivenessActor.swift into your project. This class contains the method on how to create and initialize the "FaceAnalyzer". Specifically,

   (1) ***Configuring the FaceAPI service to obtain the required token***
   ```swift
   let token = obtainToken()
   serviceOptions = try  VisionServiceOptions(endpoint: "")
   serviceOptions?.authorizationToken = token
   ```
   (2) ***Configuring the face analyzer***
   ```swift
   let createOptions = try! FaceAnalyzerCreateOptions()
   createOptions.faceAnalyzerMode = FaceAnalyzerMode.trackFacesAcrossImageStream
   ```
   (3)  ***Configuring the Analysis method***
   ```swift
   let methodOptions = try! FaceAnalysisOptions()
   methodOptions.faceSelectionMode = FaceSelectionMode.largest
   ```
   (4)  ***Initializing the face analyzer***
   ```swift
   faceAnalyzer = await try FaceAnalyzer.create(serviceOptions: serviceOptions, input: visionSource, createOptions: createOptions)
   ```
   (5)  ***Registering listeners for analyzer events and results***
   ```swift
   faceAnalyzer?.addAnalyzingEventHandler{(analyzer: FaceAnalyzer, result: FaceAnalyzingResult) in
       let faces = result.faces
       let face = faces?[faces!.startIndex]
       // your analyzingEventHandler
   }

   faceAnalyzer?.addAnalyzedEventHandler {[parameters] (analyzer: FaceAnalyzer, result: FaceAnalyzedResult) in
       let faces = result.faces
       let face = faces?[faces!.startIndex]
       // your analyzedEventHandler
   }
   ```
   (6)  ***Starting the analyzer***
   ```swift
   faceAnalyzer?.analyzeOnce(using: methodOptions, completionHandler: { (result, error) in
       // your analyzeOnce completionHandler
   })
   ```

6. **Copy the required utility methods**

   We also provide some convenience extention and utility methods as you may see in the sample App. Please copy them as needed.

7. **Localization**

   All the on-screen prompt are defined with English as default language. Please refer to the Localization.swift for your localization needs.

## Next steps:

For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.