---
page_type: sample
languages:
- cpp
- csharp
- python
- swift
- kotlin
name: "Microsoft Azure AI Vision SDK Samples"
description: "Learn how to use the Microsoft Azure AI Vision SDK to add computer vision features to your apps."
products:
- azure
- azure-computer-vision
---

# Azure AI Vision SDK (Preview) Samples

This repository hosts sample code and setup documents for the Microsoft Azure AI Vision SDK (Preview).

## News

* **Vision SDK 0.16.0-beta.1** released November 2023 that adds Azure AI Vision Face features.
* **Vision SDK 0.15.1-beta.1** released September 2023.
  * Add support for Java JRE on Windows x64 and Linux x64.
  * Input image can now be provided from a memory buffer (in addition to from file or URL).
* **Vision SDK 0.13.0-beta.1** released July 2023. Image Analysis support was added for Universal Windows Platform (UWP) applications (C++, C#). Run-time package size reduction: Only the two native binaries 
`Azure-AI-Vision-Native.dll` and `Azure-AI-Vision-Extension-Image.dll` are now needed.
* **Vision SDK 0.11.1-beta.1** released May 2023. Image Analysis APIs were updated to support [Background Removal](https://learn.microsoft.com/azure/ai-services/computer-vision/concept-background-removal).
* **Vision SDK 0.10.0-beta.1** released April 2023. Image Analysis APIs were updated to support [Dense Captions](https://learn.microsoft.com/azure/ai-services/computer-vision/concept-describe-images-40?tabs=dense).
* **Vision SDK 0.9.0-beta.1** first released on March 2023, targeting Image Analysis applications on Windows and Linux platforms.

## Features

This repository hosts samples that help you get started with several features of the SDK in public preview. This includes the following API sets:

* [Image Analysis](#image-analysis)
* [Face Analysis](#face-analysis)

Other API sets are under development.

## Support

Please [open a new issue in this repo](https://github.com/Azure-Samples/azure-ai-vision-sdk/issues) if you encounter any problems building or running the samples, or have any additional questions about the SDK. This is the preferred method of getting support. Note that these issues will be visible to the public, so please do not include any sensitive information.

Alternatively, you can contact Microsoft's Vision SDK development team directly by sending an e-mail to  `vision-sdk@microsoft.com`.

## Get the SDK samples

* **Running the samples in this repository requires you to install the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* The easiest way to get access to these samples is to download the content of this repo as a [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip).

* Alternatively, you can use a Git client to clone this repository to your hard drive by running

  ```
  git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git
  ```

## Image Analysis

### Overview

![GitHub Logo](docs/image-analysis/image-analysis-results.png)

See Microsoft documentation for an overview of [Image Analysis](https://learn.microsoft.com/azure/ai-services/computer-vision/overview-image-analysis). The Vision SDK Image Analysis APIs (preview) uses [Image Analysis REST API v4.0 (preview)](https://aka.ms/vision-4-0-ref). 

The Image Analysis APIs supports the extraction of one or more of the following visual features using a single REST call:

* **Caption** - Generates a human-readable phrase that describes the whole image content. For example, for the above image, "A woman wearing a mask sitting at a table with a laptop".
* **Dense Captions** - Generates a human-readable phrase that describes the whole image content, and up to 9 additional descriptions that describe sub-regions of the image.
* **Tags** - Returns content tags for recognizable objects, living beings, scenery, and actions that appear in the image.
* **Objects** - Detects various objects within an image, including their approximate location. See example in the above image: person, two chairs, laptop, dining table.
* **People** - Detects people in the image, including their approximate location.
* **Text** - Also known as **Read** or **OCR**. Performs Optical Character Recognition (OCR) and returns the text detected in the image, including the approximate location of every text line and word.
* **Crop Suggestions** - Also known as **Smart Crop**. Recommendations for cropping operations that preserve content (e.g. for thumbnail generation).

The Image Analysis APIs also support **background removal** (segmentation). This feature can either output an image of the detected foreground object with a transparent background, or a gray-scale alpha matte image showing the opacity of the detected foreground object.

For all scenarios, you can either upload an image for analysis by providing the name of an image file on disk, or you can provide a publicly-accessible URL of the image.

### Supported Programming Languages and Platforms

At the moment the SDK is available for the following platforms and programming languages:

* Platforms:
  * Windows 10 x64 (and above)
  * Linux x64 running Ubuntu 18.04/20.04/22.04, Debian 9/10/11, Red Hat Enterprise Linux (RHEL) 7/8

* Programming languages:
  * Python
  * C# (.NET)
  * Java JRE
  * C++

Support for others platform and programming languages (including Android, iOS, MacOS) is planned for future releases.

If your platform and/or programming language is not listed above, your application will need to directly implement REST calls to the Vision Service using the [Image Analysis REST API v4.0 (preview)](https://aka.ms/vision-4-0-ref).

### Samples

The samples will show how to analyze an image file from local disk or an image URL. Click on the links below for detailed setup, build and run instructions corresponding to your programming language:

| Programming Language |
| -------- |
| [Python Console Sample](samples/python/image-analysis) |
| [C# .NET Console Sample](samples/csharp/image-analysis/dotnet) |
| [C# .NET UWP Sample](samples/csharp/image-analysis/uwp) |
| [Java JRE Sample](samples/java/image-analysis) |
| [C++ Console Sample](samples/cpp/image-analysis) |

The console samples demonstrate doing the following:

1. Analyze all features from a JPEG image file on disk and print detailed results to the console. This is done using the synchronous (blocking) API. It is recommended you start by looking at this sample.
1. Analyze one feature from an image URL, using the asynchronous (non-blocking) API, while registering for an event to get the analysis results.
1. Analyze one feature from an image in an input memory buffer, using synchronous (blocking) API.
1. Analyze an image using a custom-trained model. To run this sample, you first need to create a custom model. See [Create a custom Image Analysis model (preview)](https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/model-customization) for more details.
1. Analyze an image for background removal (segmentation).

The C# .NET UWP sample shows how to analyze features from an image file or image URL.

If your platform and/or programming language is not listed above, your application will need to directly implement REST calls to the Vision service using the [Image Analysis REST API v4.0 (preview)](https://aka.ms/vision-4-0-ref).

### API Reference Documentation

* [Python API Reference Documents](https://learn.microsoft.com/python/api/azure-ai-vision)
* [C# .NET Core API Reference Documents](https://learn.microsoft.com/dotnet/api/azure.ai.vision.imageanalysis)
* [JAVA JRE API Reference Documents](https://learn.microsoft.com/java/api/com.azure.ai.vision.imageanalysis)
* [C++ API Reference Documents](https://learn.microsoft.com/cpp/cognitive-services/vision)


## Face Analysis

### Overview

![face](docs/face/face-reco-mobile.jpg)
See Microsoft documentation for an overview of [Azure AI Vision Face Liveness Detection](https://aka.ms/azure-ai-vision-face-liveness-tutorial).

This SDK supports two feature variants:
- Liveness with Verification
- Liveness

Liveness detection aims to verify that the system engages with a physically present, living individual during the verification process. This is achieved by differentiating between a real (live) and fake (spoof) representation which may include photographs, videos, masks, or other means to mimic a real person.

The new Face liveness detection solution is a combination of mobile SDK and Azure AI service. It is conformant to ISO/IEC 30107-3 PAD (Presentation Attack Detection) standards as validated by iBeta level 1 and level 2 conformance testing. It successfully defends against a plethora of spoof types ranging from paper printouts, 2D/3D masks, and spoof presentations on phones and laptops. Liveness detection is an active area of research, with continuous improvements being made to counteract increasingly sophisticated spoofing attacks over time, and continuous improvement will be rolled out to the client and the service components as the overall solution gets hardened against new types of attacks over time.

While blocking spoof attacks is the primary focus of the liveness solution, paramount importance is also given to allowing real users to successfully pass the liveness check with low friction. Additionally, the liveness solution complies with the comprehensive responsible AI and data privacy standards to ensure fair usage across demographics around the world through extensive fairness testing. For more information, please visit: [Empowering responsible AI practices | Microsoft AI](https://www.microsoft.com/ai/responsible-ai).

Please see the readme documents listed below for instructions on how to build and run each sample. 

### Prerequisite

- You will need to get access to the SDK artifacts in order to run these samples. To get started you would need to apply for the [Face Recognition Limited Access Features](https://customervoice.microsoft.com/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR7en2Ais5pxKtso_Pz4b1_xUQjA5SkYzNDM4TkcwQzNEOE1NVEdKUUlRRCQlQCN0PWcu) to get access to the SDK artifacts. Once you are approved please refer to the [GET_FACE_ARTIFACTS_ACCESS.md](./GET_FACE_ARTIFACTS_ACCESS.md) file to get access to the the SDK artifacts and the [Swift_README](./samples/swift/face/FaceAnalyzerSample/README.md), [Kotlin_README](./samples/kotlin/face/FaceAnalyzerSample/README.md) to integrate the SDK into the sample application. For more information, see the [Face Limited Access](https://learn.microsoft.com/legal/cognitive-services/computer-vision/limited-access-identity?context=%2Fazure%2Fcognitive-services%2Fcomputer-vision%2Fcontext%2Fcontext) page.  

### Samples

| Sample                                                   | Platform | Description                              |
| ------------------------------------------------------------ | -------- | ---------------------------------------- |
| [Kotlin sample app for Android](samples/kotlin/face) | Android | App with source code that demonstrates face analysis on Android |
| [Swift sample app for iOS](samples/swift/face) | iOS | App with source code that demonstrates face analysis on iOS |


### API Reference Documentation

* Kotlin API reference documents: [Azure SDK for Android](https://azure.github.io/azure-sdk-for-android/), [azure-ai-vision-common](https://azure.github.io/azure-sdk-for-android/azure-ai-vision-common/index.html), [azure-ai-vision-faceanalyzer](https://azure.github.io/azure-sdk-for-android/azure-ai-vision-faceanalyzer/com/azure/android/ai/vision/faceanalyzer/package-summary.html)
* Swift API reference documents: [Azure SDK for iOS Docs](https://azure.github.io/azure-sdk-for-ios/), [AzureAIVisionCore](https://azure.github.io/azure-sdk-for-ios/AzureAIVisionCore/index.html), [AzureAIVisionFace](https://azure.github.io/azure-sdk-for-ios/AzureAIVisionFace/index.html)