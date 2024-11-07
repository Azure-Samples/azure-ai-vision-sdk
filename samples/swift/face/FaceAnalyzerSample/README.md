# Get started with the Azure AI Vision Face UI SDK for iOS (Preview)

In this sample, you will learn how to build and run the face liveness detection application. The Azure AI Vision Face UI SDK for iOS is currently in preview. The APIs are subject to change.

### API Reference Documentation

* Swift API reference documents: [Azure SDK for iOS Docs](https://azure.github.io/azure-sdk-for-ios/), [AzureAIVisionFaceUI](https://azure.github.io/azure-sdk-for-ios/AzureAIVisionFaceUI/index.html)

## Prerequisites

1. An Azure Face API resource subscription.
2. A Mac (with iOS development environment including Xcode 13+) and an iPhone (with iOS version 14 or above) to test the AzureAIVision SDK.
3. An Apple developer account to install and run development apps on the iPhone.

## Set up the environment 
1. If this is your first time using your Mac to develop, you should build a sample app from [About Me &#x2014; Sample Apps Tutorials | Apple Developer Documentation](https://developer.apple.com/tutorials/sample-apps/aboutme) and run it on your phone before you attempt to build the App here. This will help ensure that your developer environment has been setup properly.
2. If you have a valid Azure subscription that has been provisioned for Face API Liveness Detection, you can get the access token to access the release artifacts. More details can be found in [GET_FACE_ARTIFACTS_ACCESS](../../../../GET_FACE_ARTIFACTS_ACCESS.md).
3. To install the SDK packages for your iOS development Application in Xcode, here are ways through CocoaPods or Swift Package Manager:

   - Prerequisite
      - You may encounter error if your Xcode has never been configured to use Git LFS. If Git LFS is never installed on your machine, refer to [Git LFS official site](https://git-lfs.github.com/) for instructions on how to install.
      - An example installation command in macOS is:
         ```
         brew install git-lfs
         ```
      - Make sure you initialize Git LFS after installation is done:
         ```
         git lfs install
         ```
      - You can verify your Git LFS installation by checking the version:
         ```
         git lfs --version
         ```

   - Swift Package Manager ([Swift.org - Package Manager](https://www.swift.org/documentation/package-manager/))

      - This approach requires XCode recognize and execute `git-lfs` command. However, the execution of `git-lfs` may be impacted by your macOS [System Integrity Protection (SIP)](https://developer.apple.com/documentation/security/disabling_and_enabling_system_integrity_protection). First, please check whether SIP is enabled or not by running in your macOS Terminal:
         ```
         csrutil status
         ```

      - If SIP is not enabled, or you temporarily disabled it. You can run the following command to create symbolic link and make Xcode recognize the `git-lfs` command:

         ```sh
         sudo ln -s $(which git-lfs) $(xcode-select -p)/usr/bin/git-lfs
         ```

      - After you configured Git LFS successfully, use the following repository URL in Swift Package Manager:

         ```
         https://msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.xcframework
         ````

      - You will see a pop-up window asking for username and password. Make a random username and use the accessToken from previous step to be the password.

         Note: the username is just a placeholder, and it can be any random string.

      - If your Xcode's Git cannot be configured to enable Git LFS, [switch to using your system Git](https://developer.apple.com/documentation/xcode/building-swift-packages-or-apps-that-use-them-in-continuous-integration-workflows#Use-your-systems-Git-tooling), as follows:

         - When you encounter error that says "Package Resolution Failed", dismiss with "Add Anyway". This will add a partially configured package dependency.

         - If Xcode Command Line Tools is never installed on your machine, install it first [following instructions from Apple Developer website](https://developer.apple.com/library/archive/technotes/tn2339/_index.html).

         - Run the following command from Terminal, from the directory where your .xcodeproj is located, as appropriate for your project. It will resolve the package through your system Git. Your system Git should already have Git LFS configured, as mentioned in Prerequisites section.

            ```
            xcodebuild -scmProvider system -resolvePackageDependencies
            ```

            > **Note**: You can also add `-project`, `-workspace`, and `-scheme` arguments as appropriate to target your development setup. Refer to the [xcodebuild (Command Line Tools) FAQ](https://developer.apple.com/library/archive/technotes/tn2339/_index.html) and `-help` section.

         - Once the command above ran successfully, your project should be able to successfully resolve the dependency from Xcode as well.

      - Alternatively, if you still cannot resolve Git LFS issue for Swift Package Manager, you can download the packages by cloning the source Git repositories directly. This should be the least preferred method, as it requires manual dependency version management and may lead to staleness or version conflicts.

         - use the access token from [GET_FACE_ARTIFACTS_ACCESS](../../../../GET_FACE_ARTIFACTS_ACCESS.md) as a "password" to clone the following repositories, then manually copy the files to your project.
            ```
            git clone https://username:{accessToken}@msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.xcframework 
            ```

            Note: "accessToken" is the only required parameter here.

         - After cloning the repositories, you should see 'AzureAIVisionFaceUI.xcframework' as a folder in your local path. The framework you should use is located under the parent folder, like:
            ```
            AzureAIVisionFaceUI.xcframework/AzureAIVisionFaceUI.xcframework
            ```
            Ensure their size on disk is larger than 100MB. If not, check your Git LFS installation and initialization, then run the following commands in each repository directory:
            ```
            git lfs pull
            ```
         - Open your Xcode project and navigate to Target -> General -> Frameworks, Libraries, and Embedded Content. Remove any existing Swift Package Manager dependencies for 'AzureAIVisionFaceUI.xcframework' if it is defined that way. Choose "Add Other", then "Add files", and add the framework from your cloned repositories path:
            ```
            localPath\AzureAIVisionFaceUI.xcframework\AzureAIVisionFaceUI.xcframework
            ```
            Mark them as "Do Not Embed".

   - CocoaPods ([CocoaPods Guides - Getting Started](https://guides.cocoapods.org/using/getting-started.html))
      - Add the following lines to your project's Podfile. `'YourBuildTargetNameHere'` is an example target, and you should use your actual target project instead. You can also [specify your version requirement](https://guides.cocoapods.org/using/the-podfile.html#specifying-pod-versions) as needed.

         ```ruby
         # add repo as source
         source 'https://msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.podspec'

         target 'YourBuildTargetNameHere' do
            # add the pod here, optionally with version specification as needed
            pod 'AzureAIVisionFaceUI'
         end
         ```

      - For access authorization to the repos, the steps depend on your system Git and your security preferences.
         - If you are using Git credential manager, you will be prompted for username and password.

            Note: the username is just a placeholder, and it can be any random string.
         - To use [`http.extraHeader` approach of `git-config`](https://git-scm.com/docs/git-config/2.22.0#Documentation/git-config.txt-httpextraHeader), you need to convert the token to base64 format. Refer to [the **Use a PAT** section of this Azure DevOps documentation article](https://learn.microsoft.com/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Linux#use-a-pat). Note that instead of using the git clone invocation as shown in the example, you should call:

            ```
            MY_PAT=accessToken
            HEADER_VALUE=$(printf "Authorization: Basic %s" "$MY_PAT" | base64)
            git config --global http.https://msface.visualstudio.com/SDK.extraHeader "${HEADER_VALUE}"
            ```

         - For other types of Git installation, refer to [the **Credentials** section of Git FAQ](https://git-scm.com/docs/gitfaq#_credentials).

4. [Refer to the API reference documentation](#api-reference-documentation) to learn more about our SDK APIs.

## Next steps
Now that you have setup your environment you can either:

- [Build and run sample app](#build-and-run-sample-app) 
- [Integrate face liveness detection into your own application](#integrate-face-analysis-into-your-own-application)

## Build and run sample App

1. Download the sample App folder. Double click the .xcodeproj file. This will open the project in Xcode.
2. Add package dependency through Swift Package Manager, as mentioned before. You should add AzureAIVisionFaceUI.xcframework into the project. If you failed to use Swift Package Manager to add the frameworks, you may also consider using alternative ways like cloning the source Git repositories or CocoaPods in [Set up the environment](#set-up-the-environment).
3. Set the App bundle identifier and developer team in "XCode -> Targets -> Signing & Capabilities" using your Apple developer account information.
4. Now attach your phone to the Mac. You should get prompt on the phone asking you to "Trust" the Mac. Enable the trust.
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

## Integrate face liveness detection into your own application

Based on the provided sample App, please refer to `MainView.swift` for an example usage of the `FaceLivenessDetectorView`. Here are the steps to integrate the face liveness detection into your own application:

### 1. Configure your Xcode project

   1. in the "Xcode -> Targets -> Build Settings -> Swift Compiler - Language", select the "C++ and Objective-C Interoperability" to be "C++ / Objective-C++

   2. in the "Xcode -> Targets -> Info -> Custom iOS Target Properties", add Key "Privacy-Camera Usage Description" with your description, like "This App requires camera usage."

### 2. Add package dependency

   In the "Xcode -> Files -> Add Package Dependencies", add AzureAIVisionFaceUI.xcframework as mentioned in [Set up the environment](#set-up-the-environment).

### 3. Insert FaceLivenessDetectorView into your Screen-type View.

Please refer to `MainView.swift` for an example usage of the `FaceLivenessDetectorView`.
Here are more details about the `FaceLivenessDetectorView` parameters.

For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.

   1. `result: Binding<LivenessDetectionResult?>`
   
      **Required parameter.**
      The result of liveness detection. It is only `nil` while the analysis has not completed.

      Define a `@Binding` of type `LivenessDetectionResult?` in your View to receive the result of the liveness detection, and provide this to `FaceLivenessDetectorView`.
      In `MainView.swift` example, this `View` holds the source `@State` variable of the passed binding directly.

   2. `sessionAuthorizationToken: String`

      **Required parameter.**
      Used to authorize the client and allow the client to establish the session connection to the server.

    /// - Parameter deviceCorrelationId: The client-specified correlation ID, if required.

   3. `verifyImageFileContent: Data?`

      **Optional parameter**, default as `nil`.
      This refers to the reference image, if provided in client.
      For most production scenario, you should provide this in the App server when creating the session.
      A non-`nil` value here requires that the provided `sessionAuthorizationToken` allows setting this value on client-side.
      Else, re-specifying them here will result in an error.

   4. `deviceCorrelationId: String?`

      **Optional parameter**, default as `nil`.
      This refers to the device correlation identifier, if provided in client.
      For most production scenario, you should provide this in the App server when creating the session.
      A non-`nil` value here requires that the provided `sessionAuthorizationToken` allows setting this value on client-side.
      Else, re-specifying them here will result in an error.

Next, respond to the update of the passed binding in your `View`.
In `MainView.swift` example, the `View` uses `onChange(of:perform:)` to demonstrate a more imperative way of handling the result, but you can also use a more SwiftUI-esque declarative way of handling the result, like:

```swift
struct HostView: View {
    @State var livenessDetectionResult: LivenessDetectionResult? = nil
    var token: String
    var body: some View {
        if livenessDetectionResult == nil {
            FaceLivenessDetectorView(result: $livenessDetectionResult,
                                     sessionAuthorizationToken: token)
        } else if let result = livenessDetectionResult {
            VStack {
                switch result { 
                    case .success(let result):
                    /// <#handle success#>
                    case .failure(let error):
                    /// <#handle failure#>
                }
            }
        }
    }
}
```

### 4. Configure the liveness algorithm details for advanced settings

   With first 4 steps, you should be able to run liveness detection in your own project. Here are more advanced details for you to understand the API usage. The file `LaunchView.swift` contains the method on how the token was obtained.

   1. ***Configuring the FaceAPI service to obtain the required session-authorization-token***
   ```swift
   // this is for demo purpose only, session-authorization-token can be obtained in the App server directly
   sessionData.token = obtainToken(...)
   ```

   Note:
   * A demo version on obtaining the token is in `AppUtility.swift` for the demo app to be built as an standalone solution, but this is not recommended.  The "session-authorization-token" is required to start a liveness session.  For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.

### 5. Add required localizations

   All the on-screen prompt are defined with English as default language. We provide Chinese (simplified) as an example to add your localization.

   (1) Go to "Xcode -> Targets -> Info -> Custom iOS Target Properties -> Localizations" to add all the languages you want to support.

   (2) Refer to the Core/en.lproj and Core/zh-Hans.lproj to add the corresponding translation for all the added languages in your localizations.

### 6. Add validation for the integrity of the service result

   We highly recommend leveraging the "digest" generated within the solution to validate the integrity of the communication between your application and the Azure AI Vision Face service. This is necessary to ensure that the final liveness detection result is trustworthy. "Digest" is provided in the following two locations:
1. The `FaceLivenessDetectorView` running on your application.

   In the resulting `LivenessDetectionResult`:

```swift
if case let .success(success) = result {
    sessionData.resultDigest = success.digest
}
```

2. The Azure AI Vision Face service.

   The "digest" will be contained within the liveness detection result when calling the detectliveness/singlemodal/sessions/<session-id> REST call. Look for an example of the "digest" in the [tutorial](https://aka.ms/azure-ai-vision-face-liveness-tutorial) where the liveness detection result is shown.

   Digests must match between the application and the service. We recommend using these digests in conjunction with iOS integrity APIs to perform the final validation.
   For more information on the iOS Integrity APIs, please refer to:
   - [DeviceCheck | Apple Developer Documentation](https://developer.apple.com/documentation/devicecheck)

