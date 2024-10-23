# Get started with the Azure AI Vision Face Client SDK for Android (Preview)

In this sample, you will learn basic design patterns for face recognition with liveness detection using the Azure AI Vision Face Client SDK for Android (Preview). The SDK is currently in preview and APIs are subject to change.

## Prerequisites 
* An Azure Face API resource subscription.
* A PC (Windows, Linux, Mac) with Android Studio installed.
* An Android mobile device (API level 21 or higher).

 Now you can either:
 
- [Build and run sample app](#build-the-sample-app) 
- [Integrate face analysis into your own application](#integrate-face-analysis-into-your-own-application)

## Build and run sample app
The sample app uses the Vision SDK to perform face liveness analysis. The following sections will walk you through these building and running the sample.

### Get Access Token to SDK Artifact
The access token is used for maven authentication.  The solution uses azure maven repo artifact to add the binary enabling the liveness feature.  You will need to set up azure maven repo with any username and valid "access token" as "password".  This token will be used as `mavenPassword` in the [Add Build Dependency](#step-4-add-build-dependency) section below.
See [GET_FACE_ARTIFACTS_ACCESS](../../../../GET_FACE_ARTIFACTS_ACCESS.md).

### Add Credential
You need to add credentials in `gradle.properties` to set up variable `mavenUser` and `mavenPassword` used above.  These are obtained through azure command in sdk access.  `mavenPassword` is the access token from above section.  
The creditial is going to look like:
```
mavenUser=any_username_string
mavenPassword=access_token
```

### Build the sample app
Follow these steps to try out the sample app. The app performs liveness analysis using the Vision SDK.
* Open the "FaceLivenessDetectorSample" folder on Android Studio.
* Press Ctrl+F9, or select **Build** \> **Make Project**.

### Run the sample
Follow these steps to download and launch the app on your Android device.
* Your android device must be in developer mode with USB debugging enabled.
* Check that your device has a network connection.
* Connect your device to your development PC.
* Make sure Android Studio recognizes the device. Go to Device Manager in Android Studio, click on the “Physical” tab, and check that your device listed. The app cannot run on an emulator because camera input is needed. 
* Press Shift+F10 or select **Run** \> **Run 'app'**.
* Once the app is installed on the phone, it will ask for camera and storage permissions. Allow these two permissions. 
* Click on the "Settings" button on the main page. Enter in your Face API endpoint and subscription key. Click “Save” if you made any changes.  
* You are now ready to run the app. Click each button to run through each scenario, like liveness and livenessWithVerify.

### Verification with liveness analysis

Verification is a 1-1 matching. You can verify against a face, like the photo on your ID card. 

1. Using your device's camera, take a photo of your ID card. Make sure your face is in the upright position and has not been rotated.
2. Click on the "Select Verify Image" button and it will prompt you to upload an image of a face to verify against. Upload the image of your ID card.
3. Click on the "LivenessWithVerify" button.
4. Click next and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness analysis. 
5. Once your face is analyzed, the app will display your verification and liveness results. You should expect a "recognized" and a "live" result. A recognition confidence score is also displayed. 

To test out other liveness analysis scenarios, repeat steps 1-5, this time holding up your ID card to the front-facing camera. Since this is not a live face, you should expect a "recognized" and a "spoof" result. 


### The overview of face recognition with liveness detection in Azure AI Vision SDK for Android (Preview)
Here is the outline of the SDK sample and integration structure
1. The solution uses azure maven repo artifact to add the binary enabling the liveness feature.  You will need to set up azure `maven` repo with any `username` and valid "access token" as "password.  It will be mentioned below in [Get Access Token to SDK Artifact](#step-3-get-access-token-to-sdk-artifact) section for getting the password, along with the [Add Build Dependencies](#step-4-add-build-dependency) to set the repo in the solution files.
2. The app requires camera permission.  You will need to set it up in the app configuration and code.  It will be mentioned below in [Add Camera Permission](#step-1-add-camera-permissions) and [Add Kotlin code to Request Camera Permission](#step-2-add-kotlin-code-to-request-camera-permission) sections for demostration.
3. There is an compose method called `FaceLivenessDetector`.  The compose component consists a one stop bundle for the liveness feature with UI code.
4. The compose method takes a set of parameters launching it, the parameters defines the liveness session and callback behaviour.  It will be mentioned below in [Add code to interpret the result](#step-5-add-code-to-interpret-the-result) section to demostrate how to use it.


### Step 1 Add Camera Permissions
Face analysis requires access to the camera to perform liveness analysis. You need to prompt the user to grant camera permission.  Here is how to add camera permissions and activity data in the manifest:
Add permission for the app in `AndroidManifest.xml`
```
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" />
    <uses-feature android:name="android.hardware.screen.portrait" />
```
, and now add proper code to request camera permission in kotlin as below

### Step 2 Add Kotlin code to Request Camera Permission
You can look at `MainActivity.kt` for sample on how to achieve the same or follow the guidelines for [Android](https://developer.android.com/training/permissions/requesting).  Camera permission needs to be ready before calling the livness process.
Here is part of the code piece that asks camera permission
```
    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                cAppRequestCode
            )
        }
    }
```

### Step 3 Get Access Token to SDK Artifact
The access token is used for maven authentication.  The solution uses azure maven repo artifact to add the binary enabling the liveness feature.  You will need to set up azure maven repo with any username and valid "access token" as "password".  This token will be used as `mavenPassword` in the "Add Build Dependency" section below.
See [GET_FACE_ARTIFACTS_ACCESS](../../../../GET_FACE_ARTIFACTS_ACCESS.md).

### Step 4 Add Build Dependency
* You need to add the following dependencies to apps' build.grade.kts `plugins` section.
```
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

```
* You need to add the following dependencies to apps' build.grade.kts `dependencies` section.
```
    implementation("com.azure.ai:azure-ai-vision-face-ui:0.18.0-beta.1")
    implementation("com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.10")
```
* You need to add repository in the settings.gradle.kts for dependencyResolutionManagement
```
                maven {
                    url = uri("https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/maven/v1")
                    credentials {
                        username = mavenUser
                        password = mavenPassword
                    }
                }
```
* You need to add credentials in gradle.properties to set up variable `mavenUser` and `mavenPassword` used above.  These are obtained through azure command from above [Get Access Token to SDK Artifact](#step-3-get-access-token-to-sdk-artifact) section.
```
mavenUser=any_username_string
mavenPassword=access_token
```

### Step 5 Add code to interpret the result
The activity takes a set of parameters launching it.  The parameter defines the activity callback behaviour.  The parameters for input are `sessionAuthorizationToken`, `verifyImageFileContent`, `deviceCorrelationId`.  

* sessionAuthorizationToken: session authroization token from the server
* verifyImageFileContent: when choosing livenessWithVerify and setting verify image in the client, this is the ByteArray of the file content of the image.  Otherwise it should be `null`.
* deviceCorrelationId: when choosing not to set deviceCorrelationId in the token creation time, you can put the deviceCorrelationId here.  Otherwise it should be `null`.

The parameters for the callback are `OnSuccess` and `OnError` method.

* OnSuccess: Get called back when the session is completed successfully with `LivenessDetectionSuccess`.  `LivenessDetectionSuccess` shows the liveness session result.

Inside  `LivenessDetectionSuccess`
* digest The validation string to be used to verify the communication for this call is secure.  For more information check section below [Add validation for the integrity of the service result](#step-8-add-validation-for-the-integrity-of-the-service-result)


* OnError: Get called back when the session is completed successfully with `LivenessDetectionError`.  `LivenessDetectionError` shows the liveness session error reasons.


```
fun onSuccess(livenessDetectionSuccess: LivenessDetectionSuccess) {
        
        
    }

    fun onError(livenessDetectionError: LivenessDetectionError) {
        
        
    }
```
### Step 6 Run liveness flow
Add FaceLivenessDetector in the compose UI.
* "endpoint" is the url for the endpoint server address.

* "session authorization token" should be obtained in App Server.  A demo version on obtaining the token is in `MainScreenViewModel.kt` for the demo app to be built as an standalone solution, but this is not recommended.  The session-authorization-token is required to start a liveness session.  For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.

Then the compose component can be called with the code:
```
FaceLivenessDetector(
            sessionAuthorizationToken = FaceSessionToken.sessionToken,
            verifyImageFileContent = FaceSessionToken.sessionSetInClientVerifyImage,
            deviceCorrelationId = null,
            onSuccess = viewModel::onSuccess,
            onError = viewModel::onError
        )
```

### Step 7 Localization
The feedback messages guiding the user to progress through the process is located inside the bundle.  You can localize the app by adding the string resources to the correct language folder.
For example, to add Chinese(Tranditional TW) in the resouces for the feedback messages, a folder named "values-zh-rTW" can be created and you can copy all the string xml files from the "values" folder to the specific locale folder, and translate them into Chinese(Tranditional TW).  When the Android OS language is set to Chinese(Tranditional TW), android will automatically load string resources from "values-zh-rTW" folder instead of "values" folder first.
Here is a list of string name pairs inside the bundle for customization.  When overwritten by the app you can customize the feedback
```
<resources>
    <string name="azure_ai_face_analyzer_feedback_empty"></string>
    <string name="azure_ai_face_analyzer_feedback_hold_still">Hold still.</string>
    <string name="azure_ai_face_analyzer_feedback_starting_up">Starting up...</string>
    <string name="azure_ai_face_analyzer_feedback_look_at_camera">Look at the camera.</string>
    <string name="azure_ai_face_analyzer_feedback_face_not_centered">Center your face in the circle.</string>
    <string name="azure_ai_face_analyzer_feedback_move_closer">Too far away! Move in closer.</string>
    <string name="azure_ai_face_analyzer_feedback_continue_to_move_closer">Continue to move closer.</string>
    <string name="azure_ai_face_analyzer_feedback_move_back">Too close! Move farther away.</string>
    <string name="azure_ai_face_analyzer_feedback_reduce_movement">Too much movement.</string>
    <string name="azure_ai_face_analyzer_feedback_smile">Smile</string>
    <string name="azure_ai_face_analyzer_feedback_smile_descriptive">Smile to confirm you\'re human</string>
    <string name="azure_ai_face_analyzer_feedback_look_up">Look up.</string>
    <string name="azure_ai_face_analyzer_feedback_look_up_right">Look up right.</string>
    <string name="azure_ai_face_analyzer_feedback_look_right">Look right.</string>
    <string name="azure_ai_face_analyzer_feedback_look_down_right">Look down right.</string>
    <string name="azure_ai_face_analyzer_feedback_look_down">Look down.</string>
    <string name="azure_ai_face_analyzer_feedback_look_down_left">Look down left.</string>
    <string name="azure_ai_face_analyzer_feedback_look_left">Look left.</string>
    <string name="azure_ai_face_analyzer_feedback_look_up_left">Look up left.</string>
    <string name="azure_ai_face_analyzer_feedback_attention_not_needed">Done, finishing up...</string>
    <string name="azure_ai_face_analyzer_verification_header">Face check</string>
</resources>
```

### Step 8 Add validation for the integrity of the service result
We highly recommend leveraging the "digest" generated within the solution to validate the integrity of the communication between your application and the Azure AI Vision Face service. This is necessary to ensure that the final liveness detection result is trustworthy. "Digest" is provided in the following two locations:
1. "digest" property in resultReceiver shown in [Step 5 Add code to interpret the result](#step-5-add-code-to-interpret-the-result)
2. The Azure AI Vision Face service.

   The "digest" will be contained within the liveness detection result when calling the detectliveness/singlemodal/sessions/<session-id> REST call. Look for an example of the "digest" in the [tutorial](https://aka.ms/azure-ai-vision-face-liveness-tutorial) where the liveness detection result is shown.

   Digests must match between the application and the service. We recommend using these digests in conjunction with platform integrity APIs to perform the final validation.
   For more information on the Integrity APIs, please refer to:
   - [Overview of the Play Integrity API](https://developer.android.com/google/play/integrity/overview)