# Get started with the Azure AI Vision Face UI SDK for Flutter
In this sample, you will learn how to build and run the face liveness detection application in flutter.

> **Contents**
>
> * [API Reference Documentation](#api-reference-documentation)
> * [Prerequisites](#prerequisites)
> * * [Flutter Installation Guide](#flutter-installation-guide)
>   * [Prerequisites For Android](#prerequisites-for-android)
>   * [Prerequisites For iOS](#prerequisites-for-ios)
> * [ANDROID Integration](#android-integration)
> * [iOS Integration](#iOS-integration)


## API Reference Documentation
* For Android Kotlin API reference: [azure-ai-vision-face-ui](https://azure.github.io/azure-sdk-for-android/azure-ai-vision-face-ui/index.html)
* For iOS Swift API reference: [AzureAIVisionFaceUI](https://azure.github.io/azure-sdk-for-ios/AzureAIVisionFaceUI/index.html)

## Prerequisites 

### Flutter Installation Guide

* [Install Flutter](https://flutter.dev/get-started/)
* Make sure you have Flutter version ```3.27.0``` or higher installed.
* After installation in your terminal run this command ```flutter doctor``` to verify full setup

### Prerequisites For Android
* An Azure Face API resource subscription.
* A PC (Windows, Linux, Mac) with Flutter ```(Flutter 3.27.0)``` installed with Android Studio
* Android Studio minimum (version 2023.3) or higher.
* Make sure you have gradle version ```8.3```
* JDK verison needed for this is ```openjdk 11.0.24```
* An Android mobile device (API level 24 or higher).

### Prerequisites For iOS
* An Azure Face API resource subscription.
* A Mac (with iOS development environment, ```Xcode 16.0```), an iPhone ```(iOS 14+)``` and  Flutter ```(Flutter 3.27.0)```
* An Apple developer account to install and run development apps on the iPhone.

## ANDROID-INTEGRATION

> * [Step 1: Set up the environment](#step-1-set-up-the-environment)
>   * [Step 1.1 Get Access Token to SDK Artifact](#step-11-get-access-token-to-sdk-artifact)
>   * [Step 1.2 Add Credential](#step-12-add-credential)
> * [Step 2: Build and run sample app](#step-2-build-and-run-sample-app)
>   * [Step 2.1 Build the sample app](#step-21-build-the-sample-app)
>   * [Step 2.2 Run the sample](#step-22-run-the-sample)
>   * [Step 2.3 Verification with liveness detection](#step-23-verification-with-liveness-detection)
> * [Step 3 Integrate face liveness detection into your own application](#step-3-integrate-face-liveness-detection-into-your-own-application)
>   * [Step 3.1 The overview of face recognition with liveness detection in Azure AI Vision SDK for Android](#step-31-the-overview-of-face-recognition-with-liveness-detection-in-azure-ai-vision-sdk-for-android)
>   * [Step 3.2 Add Camera Permissions](#step-32-add-camera-permissions)
>   * [Step 3.3 Add Flutter code to Request Camera Permission](#step-33-add-flutter-code-to-request-camera-permission)
>   * [Step 3.4 Add Build Dependency](#step-34-add-build-dependency)
>   * [Step 3.5 Add code to interpret the result](#step-35-add-code-to-interpret-the-result)
>   * [Step 3.6 Run liveness flow](#step-36-run-liveness-flow)
>   * [Step 3.7 Add validation for the integrity of the service result](#step-37-add-validation-for-the-integrity-of-the-service-result)
> * [FAQ](#faq)
>   * [Q: How can I get the results of the liveness session?](#q-how-can-i-get-the-results-of-the-liveness-session)
>   * [Q: How do I provide localization?](#q-how-do-i-provide-localization)

## Step 1: Set up the environment

### Step 1.1 Get Access Token to SDK Artifact
The access token is used for maven authentication. Get the access token to access the release artifacts using the API: [Liveness Session Operations - Get Client Assets Access Token](https://learn.microsoft.com/rest/api/face/liveness-session-operations/get-client-assets-access-token?view=rest-face-v1.3-preview). The solution uses azure maven repo artifact to add the binary enabling the liveness feature.  You will need to set up azure maven repo with any username and valid "access token" as "password".  This token will be used as `mavenPassword` in the [Add Build Dependency](#step-34-add-build-dependency) section below.

### Step 1.2 A or select **Build** \> **Make Project**.

### Step 2.2 Run the sample
Follow these steps to download and launch the app on your Android device.
* Your android device must be in developer mode with USB debugging enabled.

![Developer options](README-resources/devmode.png)
* Check that your device has a network connection.
* Connect your device to your development PC.
* Make sure Android Studio recognizes the device. Go to Device Manager in Android Studio, click on the “Physical” tab, and check that your device listed. The app cannot run on an emulator because camera input is needed. 
* Press Shift+F10 or select **Run** \> **Run 'app'**.
* Once the app is installed on the phone, it will ask for camera and storage permissions. Allow these two permissions. 
* Click on the "Settings" button on the main page. Enter in your Face API endpoint and subscription key. Click “Save” if you made any changes.  
* You are now ready to run the app. Click each button to run through each scenario, like liveness and livenessWithVerify.

### Step 2.3 Verification with liveness detection

Verification is a 1-1 matching. You can verify against a face, like the photo on your ID card. 

1. Using your device's camera, take a photo of your ID card. Make sure your face is in the upright position and has not been rotated.
2. Click on the "LivenessWithVerify" button and it will prompt you to upload an image of a face to verify against. Upload the image of your ID card.
3. Click next and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness detection. 
4. Once face liveness detection completes, the app will display your verification and liveness results. You should expect a "recognized" and a "live" result. A recognition confidence score is also displayed. 

To test out other liveness detection scenarios, repeat steps 1-5, this time holding up your ID card to the front-facing camera. Since this is not a live face, you should expect a "recognized" and a "spoof" result.dd Credential
![Add Credential](README-resources/maven_cred.png)
You need to add credentials in `gradle.properties` to set up variable `mavenUser` and `mavenPassword` used above.  These are obtained through azure command in sdk access.  `mavenPassword` is the access token from above section.  
The creditial is going to look like:

```
mavenUser=any_username_string
mavenPassword=access_token
```

## Step 2: Build and run sample app
The sample app uses the Face UI SDK to perform face liveness detection. The following sections will walk you through these building and running the sample.

### Step 2.1 Build the sample app

* Before running the project, make sure to fetch the Flutter packages using ```flutter pub get```.

* In the local.properties file, ensure that the ``` sdk.dir``` and ```flutter.sdk``` paths are correctly set.

* Open the "FlutterLivenessSample"'s Android folder on Android Studio.

![Build Sample](README-resources/build.png)
Follow these steps to try out the sample app. The app performs liveness detection using the Vision SDK.


## Step 3 Integrate face liveness detection into your Flutter application
### Step 3.1 The overview of face recognition with liveness detection in Azure AI Vision SDK for Android

Here is the outline of the SDK sample and integration structure
1. The solution uses azure maven repo artifact to add the binary enabling the liveness feature.  You will need to set up azure `maven` repo with any `username` and valid "access token" as "password.  It will be mentioned in [Get Access Token to SDK Artifact](#step-11-get-access-token-to-sdk-artifact) section for getting the password, along with the [Add Build Dependencies](#add-build-dependency) to set the repo in the solution files.
2. The app requires camera permission.  You will need to set it up in the app configuration and code.  It will be mentioned below in [Add Camera Permission](#add-camera-permissions) and [Add Flutter code to Request Camera Permission](#add-flutter-code-to-request-camera-permission) sections for demonstration.
3. There is an compose method called `FaceLivenessDetector`.  The compose component consists a one stop bundle for the liveness feature with UI code.
4. The compose method takes a set of parameters launching it, the parameters defines the liveness session and callback behaviour.  It will be mentioned below in [Add code to interpret the result](#step-35-add-code-to-interpret-the-result) section to demostrate how to use it.

### Step 3.2 Add Camera Permissions
Face UI SDK requires access to the camera to perform liveness detection. You need to prompt the user to grant camera permission.  Here is how to add camera permissions and activity data in the manifest:
Add permission for the app in `AndroidManifest.xml`
```
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-feature android:name="android.hardware.camera" />
    <uses-feature android:name="android.hardware.screen.portrait" />
```

, and now add proper code to request camera permission in flutter as below

### Step 3.3 Add Flutter code to Request Camera Permission
Camera permission needs to be ready before calling the liveness process.
Here is part of the code piece that asks camera permission
```
   final cameraStatus = await Permission.camera.status;
  if (!cameraStatus.isGranted) {
    final result = await Permission.camera.request();
    if (!result.isGranted) {
      print("Camera permission denied.");
      return;
    }
  }
```

### Step 3.4 Add Build Dependency
Android Side
* You need to add the following dependencies to build.gradle (app-level):
```
dependencies {
    implementation("com.azure.android:azure-core-http-okhttp:1.0.0-beta.12")
    implementation("com.azure:azure-ai-vision-face-ui:+")
    implementation("com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.10")
    implementation('androidx.activity:activity-compose:1.7.2')
}
```
* You need to add repository in the gradle.properties for dependencyResolutionManagement
```
                maven {
                    url = uri("https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/maven/v1")
                    credentials {
                        username = mavenUser
                        password = mavenPassword
                    }
                }
```
* You need to add credentials in gradle.properties to set up variable `mavenUser` and `mavenPassword` used above.  These are obtained through azure command from above [Get Access Token to SDK Artifact](#step-11-get-access-token-to-sdk-artifact) section.
```
mavenUser=any_username_string
mavenPassword=access_token
```
* If you use ProGuard or DexGuard, please refer to [ProGuard Exceptions](#proguard-exceptions) and [DexGuard Exceptions](#dexguard-exceptions) for details.


* Enable Jetpack Compose:
```
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}
```
### Step 3.5 Add code to interpret the result
The activity takes a set of parameters launching it.  The parameter defines the activity callback behaviour.  The parameters for input are `sessionAuthorizationToken`, `verifyImageFileContent`, `deviceCorrelationId`, `userCorrelationId`.

* sessionAuthorizationToken: session authorization token from the server
* verifyImageFileContent: when choosing livenessWithVerify and setting verify image in the client, this is the ByteArray of the file content of the image.  Otherwise it should be `null`.
* deviceCorrelationId: when choosing not to set deviceCorrelationId in the token creation time, you can put the deviceCorrelationId here.  Otherwise it should be `null`.
* userCorrelationId: when choosing not to set userCorrelationId in the token creation time, you can put the userCorrelationId here.  Otherwise it should be `null`.

### Step 3.6 Run liveness flow

* Add method channel logic in Dart:
```
static const platform = MethodChannel('azure_face_liveness_channel');

Future<void> startLivenessSession(String token, Uint8List imageBytes) async {
  try {
    final result = await platform.invokeMethod('startLiveness', {
      'sessionToken': token,
      'verifyImageFileContent': imageBytes,
    });

    if (result['status'] == 'success') {
      print("Liveness Success: ${result['data']}");
    } else {
      print("Liveness Error: ${result['livenessError']} / ${result['recognitionError']}");
    }
  } on PlatformException catch (e) {
    print("PlatformException: ${e.message}");
  }
}
```

* Android Native Setup

* In MainActivity.kt file it defines and manages the methods for Flutter MethodChannel communication, enabling Flutter to invoke native Android functions and handle their responses.
* It Handles Flutter Android communication and result passing – Listens for MethodChannel calls from Flutter, executes native Android actions (such as launching activities), and sends the results back to Flutter.

```
class MainActivity : FlutterActivity() {

    private val CHANNEL = "azure_face_liveness_channel"
    private val LIVENESS_REQUEST_CODE = 1001
    private var resultCallback: MethodChannel.Result? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            if (call.method == "startLiveness") {
                val sessionToken = call.argument<String>("sessionToken") ?: ""
                val verifyImageFileContent = call.argument<ByteArray>("verifyImageFileContent")

                val intent = Intent(this, LivenessActivity::class.java).apply {
                    putExtra("sessionToken", sessionToken)
                    putExtra("verifyImageFileContent", verifyImageFileContent)
                }

                resultCallback = result
                startActivityForResult(intent, LIVENESS_REQUEST_CODE)
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LIVENESS_REQUEST_CODE && resultCallback != null) {
            val status = data?.getStringExtra("status")
            if (status == "success") {
                val resultMap = hashMapOf<String, Any?>(
                    "status" to "success",
                    "resultId" to data.getStringExtra("resultId"),
                    "digest" to data.getStringExtra("digest"),
                    "data" to data.getStringExtra("data")
                )
                resultCallback?.success(resultMap)
            } else if (status == "error") {
                val errorMap = hashMapOf<String, Any?>(
                    "status" to "error",
                    "livenessError" to data.getStringExtra("livenessError"),
                    "recognitionError" to data.getStringExtra("recognitionError"),
                    "data" to data.getStringExtra("data")
                )
                resultCallback?.success(errorMap)
            } else {
                resultCallback?.error("UNEXPECTED_RESULT", "No valid result returned", null)
            }
            resultCallback = null
        }
    }
}
```

* Create new LivenessActivity.kt file it is responsible for setting up and managing the Liveness SDK logic, handling initialization, configuration, and result callbacks.
* Launches the Azure Face Liveness SDK using Jetpack Compose – Provides a Compose-based UI flow to start the liveness detection process, display the camera view, and capture the liveness result seamlessly.

```
        setContent {
            FaceLivenessDetector(
                sessionAuthorizationToken = sessionToken,
                verifyImageFileContent = verifyImageFileContent,
                deviceCorrelationId = null,
                userCorrelationId = null,
                onSuccess = { result ->
                    val resultData = Intent().apply {
                        putExtra("status", "success")
                        putExtra("resultId", result.resultId)
                        putExtra("digest", result.digest)
                        putExtra("data", result.toString())
                    }
                    setResult(Activity.RESULT_OK, resultData)
                    finish()
                },
                onError = { error ->
                    val errorData = Intent().apply {
                        putExtra("status", "error")
                        putExtra("livenessError", error.livenessError.toString())
                        putExtra("recognitionError", error.recognitionError.toString())
                        putExtra("data", error.toString())
                    }
                    setResult(Activity.RESULT_OK, errorData)
                    finish()
                }
            )
        }
```

* Usage in Flutter

```
await startLivenessSession(
  sessionToken: 'YOUR_SESSION_TOKEN',
  imageBytes: YOUR_IMAGE_BYTES,
);
```

### Step 3.7 Add validation for the integrity of the service result
We highly recommend leveraging the "digest" generated within the solution to validate the integrity of the communication between your application and the Azure AI Vision Face service. This is necessary to ensure that the final liveness detection result is trustworthy. "Digest" is provided in the following two locations:
1. "digest" property in LivenessDetectionSuccess shown in [Step 5 Add code to interpret the result](#step-35-add-code-to-interpret-the-result)
2. The Azure AI Vision Face service.

   The "digest" will be contained within the liveness detection result when calling the detectLiveness-sessions/<session-id> REST call. Look for an example of the "digest" in the [tutorial](https://aka.ms/azure-ai-vision-face-liveness-tutorial) where the liveness detection result is shown.

   Digests must match between the application and the service. We recommend using these digests in conjunction with platform integrity APIs to perform the final validation.
   For more information on the Integrity APIs, please refer to:
   - [Overview of the Play Integrity API](https://developer.android.com/google/play/integrity/overview)



## FAQ
### Q: How can I get the results of the liveness session?

Once the session is completed, for security reasons the client does not receive the outcome whether face is live or spoof. 

You can query the result from your backend service by calling the sessions results API to get the outcome
https://aka.ms/face/liveness-session/get-liveness-session-result

### Q: How do I provide localization?

The SDK provides default localization for 75 locales.  They should work automatically.  It is provided through `values-` folder in the resources in aar. For more information, [Localize your app](https://developer.android.com/guide/topics/resources/localization).


## iOS Integration

> **Contents**
> * [Step 1: Set up the environment](#step-1-set-up-the-environment)
> * [Step 2: Build and run sample iOS app](#step-2-build-and-run-sample-ios-app)
>   * [Run the sample](#run-the-sample)
>   * [Test out key scenarios](#test-out-key-scenarios)
>     * [Liveness](#liveness)
>     * [LivenessWithVerify](#livenesswithverify)
> * [Step 3: Integrate face liveness detection into your own application](#step-3-integrate-face-liveness-detection-into-your-own-application)
> * [FAQ](#faq)
>   * [Q: How do we use CocoaPods or other package managers?](#q-how-do-we-use-cocoapods-or-other-package-managers)
>   * [Q: Are there alternatives for access authorization?](#q-are-there-alternatives-for-access-authorization)
>   * [Q: How can I get the results of the liveness session?](#q-how-can-i-get-the-results-of-the-liveness-session)
>   * [Q: If you get project not compailing for Xcode 16 and iOS 18?](#q-if-you-get-project-not-compailing-for-xcode-16-and-ios-18)


## Step 1: Set up the environment

1. For the best experience, please do not open the sample project in Xcode yet before completing the environment setup.
2. If this is your first time using your Mac to develop, you should build a sample app from [About Me &#x2014; Sample Apps Tutorials | Apple Developer Documentation](https://developer.apple.com/tutorials/sample-apps/aboutme) and run it on your phone before you attempt to build the App here. This will help ensure that your developer environment has been setup properly.
3. Get the access token to access the release artifacts using the API: [Liveness Session Operations - Get Client Assets Access Token](https://learn.microsoft.com/rest/api/face/liveness-session-operations/get-client-assets-access-token?view=rest-face-v1.3-preview).
4. Prepare Git LFS
   * If you have never installed Git LFS, refer to [Git LFS official site](https://git-lfs.github.com/) for instructions.
   * For example:

      ```sh
      # install with homebrew
      brew install git-lfs
      # verify and initialize
      git lfs --version
      git lfs install
      ```

5. The sample app project has been preconfigured to reference the SDK through Swift Package Manager (SPM). Configure the authorization of the git repository from which SPM will pull the package:

   1. Open your global git config file.

      ```sh
      # path will be shown by the following command, then open it using editor
      git config --global --show-origin --list | head -1
      # alternatively default editor will be used if using the following command
      git config --global --edit
      ```

   2. Add the following lines to the global git config file. You may leave out the comments and is provided here for completeness.

      ```config
      [credential "https://msface.visualstudio.com"]
              username = pat
              helper =
              helper = "!f() { test \"$1\" = get && echo \"password=INSERT_PAT_HERE\"; }; f"

              # get access token from Get Client Assets Access Token REST API and paste ^^^^^^^^^^^^^^^ above, replacing "INSERT_PAT_HERE".
              # username does not matter for PAT so long as it is not left blank.
              # the first blank helper line is necessary to override existing helpers and not a typo.
      ```

   * for other methods of dependency such as CocoaPods, or other methods of git authentication, please refer to the [FAQ](#faq) section of this document.

6. If Xcode Command Line Tools is never installed on your machine, install it first [following instructions from Apple Developer website](https://developer.apple.com/library/archive/technotes/tn2339/_index.html).

## Step 2: Build and run sample iOS app

* Before running the project, make sure to fetch the Flutter packages using ```flutter pub get``` and install the pods inside the iOS folder using ```pod install```.

1. In **Xcode → Targets → Signing & Capabilities**, set the App bundle identifier and developer team.
   <br><br>
   ![Signing & Capabilities](./README-resources/sample_project_signing.png)
2. Connect your iPhone to the Mac, then trust the Mac when prompted.
   <br><br>
   ![Trust This Computer](README-resources/iphone_trust.png) ![Enter Passcode to Trust](README-resources/iphone_trust_passcode.png)
3. Select your iPhone in the Xcode top bar.
   <br><br>
   ![Select your iPhone](README-resources/iphone_select.png)
4. Build and run the app.

### Run the sample

1. Allow camera permission when prompted.  
2. This sample creates token on the client, so it needs the API configuration. In production scenario, this will not be necessary. For now, go to the settings page and configure:
    * API endpoint  
    * Subscription key
3. Try one of the buttons (such as “Liveness”) to begin testing.

### Test out key scenarios

#### Liveness

1. Tap "Liveness" then "Start" and show your face.
2. The screen flashes for liveness analysis.
3. Observe the Real/Spoof status.

#### LivenessWithVerify

1. Tap "LivenessWithVerify" then select a reference face image.
2. Show your face to the camera.
3. Observe the Real/Spoof status, verification status, and confidence score.


## Step 3: Integrate face liveness detection into your own application

* Add this in you podfile and install pods. make sure you have followed set up environment steps

```
# Add Azure Face UI SDK source
source 'https://msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.podspec'
# Keep the default CocoaPods source
source 'https://cdn.cocoapods.org/'

target 'Runner' do
  use_frameworks!
  use_modular_headers!

  pod 'AzureAIVisionFaceUI'

  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
  target 'RunnerTests' do
    inherit! :search_paths
  end
end
```

* After adding this in podfile install the pods using ```pod install``` from ios folder

* Add method channel logic in Dart:
```
static const platform = MethodChannel('azure_face_liveness_channel');

Future<void> startLivenessSession(String token, Uint8List imageBytes) async {
  try {
    final result = await platform.invokeMethod('startLiveness', {
      'sessionToken': token,
    });

    if (result['status'] == 'success') {
      print("Liveness Success: ${result['data']}");
    } else {
      print("Liveness Error: ${result['livenessError']} / ${result['recognitionError']}");
    }
  } on PlatformException catch (e) {
    print("PlatformException: ${e.message}");
  }
}
```
* Create FaceLivenessViewController.swift inside the Runner folder – This file is responsible for setting up and managing the Liveness SDK logic on iOS, including initialization, configuration, and handling detection results.
* Handles the Azure Face Liveness SDK integration on iOS – Manages the liveness detection flow, presents the SDK’s UI, and processes the outcome within a native Swift view controller.

```
import UIKit
import SwiftUI
import AzureAIVisionFaceUI

class LivenessViewHostingController: UIHostingController<AnyView> {
    
    var sessionToken: String
    var flutterResult: FlutterResult?
    @Environment(\.presentationMode) var presentationMode
    
    @objc required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    init(sessionToken: String, flutterResult: @escaping FlutterResult) {
        self.sessionToken = sessionToken
        self.flutterResult = flutterResult
        
        // Create the binding result object
        let resultBinding = Binding<LivenessDetectionResult?>(
            get: { nil },
            set: { result in
                guard let result = result else { return }
                
                var resultMap: [String: Any] = [:]
                
                switch result {
                case .success(let success):
                    resultMap["status"] = "success"
                    resultMap["resultId"] = success.resultId
                    resultMap["digest"] = success.digest
                    
                case .failure(let failure):
                    resultMap["status"] = "failure"
                    resultMap["message"] = failure.localizedDescription
                    if let resultId = failure.resultId {
                        resultMap["resultId"] = resultId
                    }
                }

                flutterResult(resultMap)
                
                // Dismiss the controller
                UIApplication.shared.windows.first?.rootViewController?.dismiss(animated: true, completion: nil)
            }
        )
        
        // Create the SwiftUI view
        let view = FaceLivenessDetectorView(result: resultBinding,
                                            sessionAuthorizationToken: sessionToken)
        
        super.init(rootView: AnyView(view))
    }
}
```

* In AppDelegate.swift, make the necessary changes to set up Flutter MethodChannel communication – Establishes a communication bridge between Flutter and native iOS code by defining method handlers.
* Handles Flutter → iOS communication and result passing – Listens for MethodChannel calls from Flutter, executes native Swift functions (such as launching the Face Liveness flow), and returns results back to Flutter.

```
import Flutter
import UIKit


@main
@objc class AppDelegate: FlutterAppDelegate {
    private let channelName = "azure_face_liveness_channel"

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let controller = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(name: channelName, binaryMessenger: controller.binaryMessenger)

        channel.setMethodCallHandler { (call, result) in
            if call.method == "startLiveness" {
                guard let args = call.arguments as? [String: Any],
                      let token = args["sessionToken"] as? String else {
                    result(FlutterError(code: "INVALID_ARGS", message: "Missing sessionToken", details: nil))
                    return
                }

                let livenessController = LivenessViewHostingController(sessionToken: token, flutterResult: result)
                controller.present(livenessController, animated: true, completion: nil)
            }
        }
        GeneratedPluginRegistrant.register(with: self)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
```
* Usage in Flutter

```
await startLivenessSession(
  sessionToken: 'YOUR_SESSION_TOKEN',
);
```

## FAQ

### Q: How do we use CocoaPods or other package managers?

Add the following lines to your project's Podfile. `'YourBuildTargetNameHere'` is an example target, and you should use your actual target project instead. You can also [specify your version requirement](https://guides.cocoapods.org/using/the-podfile.html#specifying-pod-versions) as needed.

```ruby
# add repo as source
source 'https://msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.podspec'
target 'YourBuildTargetNameHere' do
   # add the pod here, optionally with version specification as needed
   pod 'AzureAIVisionFaceUI'
end
```

Also read: CocoaPods ([CocoaPods Guides - Getting Started](https://guides.cocoapods.org/using/getting-started.html))

For other package managers, please consult their documentation and clone the framework repo manually.

### Q: Are there alternatives for access authorization?

There are some situations where the example plaintext token inside global `git-config` may not be suitable for your needs, such as automated build machines.

If you are using `git-credential-manager`, `credential.azreposCredentialType` needs to be set to `pat`.

The example above uses `credential.helper` approach of `git-config`. Aside from storing it directly inside the config file, there are alternate ways to provide the token to `credential.helper`. Read [custom helpers section of the `gitcredentials` documentation](https://git-scm.com/docs/gitcredentials#_custom_helpers) for more information.

To use [`http.extraHeader` approach of `git-config`](https://git-scm.com/docs/git-config/2.22.0#Documentation/git-config.txt-httpextraHeader), you need to convert the token to base64 format. Refer to [the **Use a PAT** section of this Azure DevOps documentation article](https://learn.microsoft.com/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Linux#use-a-pat). Note that instead of using the git clone invocation as shown in the example, you should call:

```sh
MY_PAT=accessToken
HEADER_VALUE=$(printf "Authorization: Basic %s" "$MY_PAT" | base64)
git config --global http.https://msface.visualstudio.com/SDK.extraHeader "${HEADER_VALUE}"
```

For other types of Git installation, refer to [the **Credentials** section of Git FAQ](https://git-scm.com/docs/gitfaq#_credentials).

### Q: How can I get the results of the liveness session?

Once the session is completed, for security reasons the client does not receive the outcome whether face is live or spoof.

You can query the result from your backend service by calling the sessions results API to get the outcome [[API Reference](https://aka.ms/face/liveness-session/get-liveness-session-result)].

### Q: If you get project not compiling for Xcode 16 and iOS 18?

Apply this fix to successfully run the iOS project for this error.

File -> Workspace Settings -> Default Location

to

File -> Workspace Settings -> Workspace-relative Location