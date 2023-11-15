# Get started with the Azure AI Vision SDK for Android (Preview)

In this sample, you will learn basic design patterns for face recognition with liveness detection using the Azure AI Vision SDK for Android (Preview). The SDK is currently in preview and APIs are subject to change.

## Prerequisites
* A subscription key and endpoint for the vision service. See the link "Already using Azure? Try this service for free now" on [Microsoft Cognitive Services](https://azure.microsoft.com/services/cognitive-services/computer-vision/).
* A PC (Windows, Linux, Mac) with Android Studio installed.
* An Android mobile device (API level 21 or higher).
* Get the API reference documentation. This will be included in the latest release artifacts here: https://github.com/Azure-Samples/azure-ai-vision-sdk/releases

## Set up the environment
To install the SDK, download the AAR file from the latest release artifact here: https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases
- You will need to get access to the SDK artifacts in order to run these samples. To get started you would need to apply for the [Face Recognition Limited Access Features](https://customervoice.microsoft.com/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR7en2Ais5pxKtso_Pz4b1_xUQjA5SkYzNDM4TkcwQzNEOE1NVEdKUUlRRCQlQCN0PWcu) to get access to the SDK artifacts. Please email [azureface@microsoft.com](azureface@microsoft.com) to get instructions on how to download the SDK. For more information, see the [Face Limited Access](https://learn.microsoft.com/en-us/legal/cognitive-services/computer-vision/limited-access-identity?context=%2Fazure%2Fcognitive-services%2Fcomputer-vision%2Fcontext%2Fcontext) page.

## Next Steps
 Now that you have setup your environment you can either:

- [Build and run sample app](#Build-and-run-sample-app) 
- [Integrate face analysis into your own application](#Integrate-face-analysis-into-your-own-application)

## Build and run sample app
The sample app uses the Vision SDK to perform face liveness analysis. The following sections will walk you through these building and running the sample.

### Build the sample app
Follow these steps to try out the sample app. The app performs liveness analysis using the Vision SDK.
* Locate and copy Vision AAR files to FaceAnalyzerSample/libs folder.
* Open the "FaceAnalyzerSample" folder on Android Studio.
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
3. Click on the "livenessWithVerify" button and it will prompt you to upload an image of a face to verify against. Upload the image of your ID card.
4. Click next and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness analysis. 
5. Once your face is analyzed, the app will display your verification and liveness results. You should expect a "recognized" and a "live" result. A recognition confidence score is also displayed. 

To test out other liveness analysis scenarios, repeat steps 1-5, this time holding up your ID card to the front-facing camera. Since this is not a live face, you should expect a "recognized" and a "spoof" result. 


## Integrate face analysis into your own application

Follow these steps to integrate the face analysis in your app to perform face liveness analysis

### Camera Permissions
Face analysis requires access to the camera to perform liveness analysis. You need to prompt the user to grant camera permission. You can look at `MainActivity.kt` for sample on how to achieve the same or follow the guidelines for [Android](https://developer.android.com/training/permissions/requesting).

### Integrate UI View

* Use `activity_analyze.xml`` for this step.
* You need to add the following dependencies to apps' build.grade `dependencies` section.
```
implementation 'androidx.core:core-ktx:1.9.0'
implementation 'com.google.android.material:material:1.7.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
```

The UI view necessary for liveness analysis is contained in res/layout/activity_analyze.xml file. You can copy the file or follow the UI elements in your app. Ensuring that the user-interface implementation in your application matches the template provided in the Vision SDK samples is critical to ensure accurate liveness detection. This requires that the background color (white and black) and the camera preview oval (position and size) in the sample is matched as closely as possible. Furthermore, ensuring that there’s no other logos, UI-controls, or text (apart from the feedback text from the SDK) is shown on screen will help increase the accuracy of the solution.


### Integrate UI model
* Use `AnalyzeModel.kt` for this step.
* You need to add the following dependencies to apps' build.grade `plugins` section.
```
plugins {
    id 'kotlin-parcelize'
}
```
The UI model for the controller is contained in `data class AnalyzeModel`. The `MainActivity` page shows a sample on how to prepare the input data for `AnalyzeActivity` session. It is recommended to get session token from your application server and pass it to the app to avoid putting the subscription key in your app.

### Integrate UI Controller

* Use `AnalyzeActivity.kt` and `AutoFitSurfaceView.kt` for this step.
* You need to add the following dependencies to apps' build.grade `dependencies` section.
```
implementation group: 'net.sourceforge.streamsupport', name:'android-retrofuture', version: '1.7.4'
implementation 'androidx.activity:activity-ktx:1.7.2'
implementation 'androidx.appcompat:appcompat:1.5.1'
implementation 'com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.10'
implementation 'com.azure.android:azure-core-credential:1.0.0-beta.10'
implementation 'com.azure:azure-ai-vision-common-internal:0.15.1-beta.1'
def camerax_version = "1.1.0"
implementation "androidx.camera:camera-camera2:$camerax_version"
implementation "androidx.camera:camera-lifecycle:$camerax_version"
implementation fileTree(dir: '../libs', include: ['*.aar'])
```

The UI controller is self-contained in `AnalyzeActivity.kt`. The class shows how to create `FaceAnalyzer` object and listen to `analyzing` and `analyzed` events to control UI view and complete the session. For this sample, final results are shown on different page, though the results are available in `analyzed` event. You can interpret results from this event and continue with appropriate action.

It is imperative to listen to `analyzing` event and change the UI view accordingly. Here is snippet from `AnalyzeActivity` to showcase the same.
```
// Lighten/darken the screen based on liveness feedback
var requiredAction = face.actionRequiredFromApplicationTask?.action;
if (requiredAction == ActionRequiredFromApplication.BRIGHTEN_DISPLAY) {
    mBackgroundLayout.setBackgroundColor(Color.WHITE)
    face.actionRequiredFromApplicationTask.setAsCompleted()
} else if (requiredAction == ActionRequiredFromApplication.DARKEN_DISPLAY) {
    mBackgroundLayout.setBackgroundColor(Color.BLACK)
    face.actionRequiredFromApplicationTask.setAsCompleted()
}
```

The following method maps the feedback to a user-friendly string, this should be localized based on your target audience.
```
private fun MapFeedbackToMessage(feedback : FeedbackForFace): String {
    when(feedback) {
        FeedbackForFace.NONE -> return "Hold Still."
        FeedbackForFace.LOOK_AT_CAMERA -> return "Look at camera."
        FeedbackForFace.FACE_NOT_CENTERED -> return "Look at camera."
        FeedbackForFace.MOVE_CLOSER -> return "Too far away! Move in closer."
        FeedbackForFace.MOVE_BACK -> return "Too close! Move farther away."
        FeedbackForFace.REDUCE_MOVEMENT -> return "Too much movement."
        FeedbackForFace.SMILE -> return "Ready, set, smile!"
        FeedbackForFace.ATTENTION_NOT_NEEDED -> {
            mDoneAnalyzing = true
            return "Done, finishing up..."
        }
    }

    return "Hold Still."
}
```

## Next steps 
In this sample, you have learned about the key face analysis scenarios offered by the Azure AI Vision SDK for Android (Preview). For more information on how to orchestrate the liveness flow by utilizing the Azure AI Vision Face service, visit: https://aka.ms/azure-ai-vision-face-liveness-tutorial.