# Get started with the Azure AI Vision SDK for iOS (Preview)

In this sample, you will learn how to build and run the face liveness detection application. The Azure AI Vision SDK for iOS is currently in preview. The APIs are subject to change.

## Prerequisites

1. An Azure Face API resource subscription.
2. A Mac (with iOS development environment including Xcode 13+) and an iPhone (with iOS version 14 or above) to test the AzureAIVision SDK.
3. An Apple developer account to install and run development apps on the iPhone.

## Set up the environment 
1. If this is your first time using your Mac to develop, you should build a sample app from [About Me — Sample Apps Tutorials | Apple Developer Documentation](https://developer.apple.com/tutorials/sample-apps/aboutme) and run it on your phone before you attempt to build the App here. This will help ensure that your developer environment has been setup properly.
2. If you have a valid Azure Face API subscription, you can get the token to access the release artifacts. Before that, make sure you have installed the [Azure Command-Line Interface](https://learn.microsoft.com/en-us/cli/azure/)
- Run this command in your Mac terminal, you can then find the token in the response.
   ```
   token=$(az account get-access-token -o tsv | cut -f1)
   curl -X GET --header "Authorization: Bearer $token" "https://face-sdk-gating-helper.azurewebsites.net/sdk/subscriptions/{subscriptionId}/tokens?id={tokenId}"
   ```
   subscriptionId is your Azure Face API subscriptionId, and tokenId can be 0 (for primary key) or 1 (for secondary key).
3. To install the SDK package, here is the way through Swift Package Manager. 
- Use this repository URL `https://msface.visualstudio.com/SDK/_git/AzureAIVisionFace.xcframework` and `https://msface.visualstudio.com/SDK/_git/AzureAIVisionCore.xcframework` in the Swift Package Manager.
- You will see a pop-up window asking for username and password. Make a random username and use the token from step 2 to be the password.
- You may encounter error if your Xcode has never been configured to use Git LFS.
If Git LFS is never installed on your machine, refer to [Git LFS official site](https://git-lfs.github.com/) for instructions on how to install. To make Xcode recognize the `git-lfs` command, create symbolic link like so:

   ```sh
   sudo ln -s $(which git-lfs) $(xcode-select -p)/usr/bin/git-lfs
   ```
4. Get the API reference documentation. This will be included in the release artifacts.

## Next steps
Now that you have setup your environment you can either:

- [Build and run sample app](#build-and-run-sample-app) 
- [Integrate face analysis into your own application](#integrate-face-analysis-into-your-own-application)

## Build and run sample App
1. Download the sample App folder. Double click the .xcodeproj file. This will open the project in Xcode.
2. Add package dependency through Swift Package Manager, as mentioned before. You should add both AzureAIVisionFace.xcframework and AzureAIVisionCore.xcframework into the project.
3. Set the App bundle identifier and developer team in XCode per your needs.
4. Now attach your phone to the Mac. You should get prompt on the phone asking you to “Trust” the Mac. Enable the trust.
5. The phone should now show up in the Xcode top bar. Your iPhone name should be visible.
6. Now build and run the app.

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

1. **Configure your Xcode project**

   (1) in the "Xcode -> Targets -> Build Settings -> Swift Compiler - Language", select the "C++ and Objective-C Interoperability" to be "C++ / Objective-C++

   (2) in the "Xcode -> Files -> Add Package Dependencies", add the AzureAIVisionCore.xcframework, AzureAIVisionFace.xcframework as mentioned in [Set up the environment](#set-up-the-environment).

   (3) in the "Xcode -> Targets -> Info -> Custom iOS Target Properties", add Key "Privacy-Camera Usage Description" with your description, like "This App requires camera usage."

2. **Copy the required files**

   Copy all the files under FaceAnalyzerSample/Core folder into your project. "LivenessView" is the view you should insert into your application to serve for the liveness detection.
   Here are more details about the LivenessView init parameters:

   (1) token: String.

   required parameter. Used to authorize and initialize the client.

   (2) withVerification: Boolean.

   Optional parameter, default as false. This boolean indicates whether the liveness detection is with verification or not.

   (3) referenceImage: UIImage?.

   Optional parameter, default as nil. This refers to the reference image provided in client. Mostly, this could be provided in the backend.

   (4) completionHandler: @escaping (String, String)->Void.

   Optional parameter, default as empty function. Used to handle the liveness detection results and also help on the UI switch possibly, as shown in the sample App.

   (5) detailsHandler: @escaping (FaceAnalyzedDetails?)->Void.
   Optional parameter, default as empty function. Used to handle the digest. You can get the digest like:
   ```
   var digest = faceAnalyzedDetails?.digest
   ```
   This is recommended to validate the integrity of the transport.

3. **Hook up the LivenessView with other views**

   Create other views per your needs, like what we have in the sample App (MainView.swift, LaunchView.swift, etc) to start/end the process, and save the face analysis results for your usage. 

4. **Configure the liveness algorithm details**

   With first 3 steps, you should be able to run liveness detection in your own project. Here is more details for you to understand the API usage. The file LivenessActor.swift contains the method on how to create and initialize the "FaceAnalyzer". Specifically,

   (1) ***Configuring the FaceAPI service to obtain the required token***
   ```swift
   // this is for demo purpose only, token can be obtained in the backend directly
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

5. **Localization**

   All the on-screen prompt are defined with English as default language. We provide Chinese (simplified) as an example to add your localization.

   (1) Go to "Xcode -> Targets -> Info -> Custom iOS Target Properties -> Localizations" to add all the languages you want to support.

   (2) Refer to the Core/en.lproj and Core/zh-Hans.lproj to add the corresponding translation for all the added languages in your localizations.

## Next steps:

For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.