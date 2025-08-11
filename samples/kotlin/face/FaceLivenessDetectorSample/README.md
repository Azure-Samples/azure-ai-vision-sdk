# Get started with the Azure AI Vision Face UI SDK for Android

This repository hosts Azure AI Vision Face UI SDK for the Android platform.

## Prerequisites

* An Azure Face API resource subscription.
* A PC (Windows, Linux, Mac) with Android Studio installed.
* An Android mobile device (API level 24 or higher).
* Follow the integration guidelines in the API reference document: [azure-ai-vision-face-ui](https://azure.github.io/azure-sdk-for-android/azure-ai-vision-face-ui/index.html)

The sample app uses the Face UI SDK to perform face liveness detection. The following sections will walk you through these building and running the sample.

### Build the sample app

![Build Sample](README-resources/build.png)
Follow these steps to try out the sample app. The app performs liveness detection using the Vision SDK.

* Open the "FaceLivenessDetectorSample" folder on Android Studio.
* Press Ctrl+F9, or select **Build** \> **Make Project**.

### Run the sample

Follow these steps to download and launch the app on your Android device.

* Your android device must be in developer mode with USB debugging enabled.
  <br><br>
  ![Developer options](README-resources/devmode.png)
* Check that your device has a network connection.
* Connect your device to your development PC.
* Make sure Android Studio recognizes the device. Go to Device Manager in Android Studio, click on the “Physical” tab, and check that your device listed. The app cannot run on an emulator because camera input is needed.
* Press Shift+F10 or select **Run** \> **Run 'app'**.
* Once the app is installed on the phone, it will ask for camera and storage permissions. Allow these two permissions.
* Click on the "Settings" button on the main page. Enter in your Face API endpoint and subscription key. Click “Save” if you made any changes.  
* You are now ready to run the app. Click each button to run through each scenario, like liveness and livenessWithVerify.

### Verification with liveness detection

Verification is a 1-1 matching. You can verify against a face, like the photo on your ID card.

1. Using your device's camera, take a photo of your ID card. Make sure your face is in the upright position and has not been rotated.
2. Click on the "LivenessWithVerify" button and it will prompt you to upload an image of a face to verify against. Upload the image of your ID card.
3. Click next and show your face to the front-facing camera. As it processes your images, the screen will display user feedback on image quality. The screen will also flash black and white. This is needed for liveness detection.
4. Once face liveness detection completes, the app will display your verification and liveness results. You should expect a "recognized" and a "live" result. A recognition confidence score is also displayed.

To test out other liveness detection scenarios, repeat steps above, this time holding up your ID card to the front-facing camera. Since this is not a live face, you should expect a "recognized" and a "spoof" result.

<!-- markdownlint-configure-file
{
  "no-inline-html": {
    "allowed_elements": [
      'br'
    ]
  }
}
-->
