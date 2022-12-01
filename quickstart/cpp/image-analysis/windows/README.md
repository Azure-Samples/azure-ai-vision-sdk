# Quickstart: Image Analysis (C++, Windows x64)

This sample demonstrates how to run Image Analysis on an image file on disk or an image URL, using Microsoft's Azure C++ Vision SDK for Windows.

Note that the Image Analysis Vision SDK is in private preview. APIs are subject to change. The SDK is not yet available to the public.

Only Windows x64 version is supported at the moment, via a NuGet Vision SDK package.

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service. You'll paste your key and endpoint into the sample code as described below.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.

* A Windows 10 (or higher) x64 PC. We only support x64 platforms at the moment.

* [Microsoft Visual Studio 2019](https://visualstudio.microsoft.com/), Community Edition or higher.

* The Desktop development with C++ workload in Visual Studio and the NuGet package manager component in Visual Studio. You can enable both in `Tools` > `Get Tools and Features`, under the `Workloads` and `Individual components` tabs, respectively.

## Compile the sample

* **By compiling this sample you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license202012)**.

* Downloaded the Azure AI Vision SDK NuGet file from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases/tag/0.7.0-beta.0.30318788) of the repository. It is named `Azure.AI.Vision.0.7.0-beta.0.30318788.nupkg`

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/archive/master.zip), or cloning this repository using a Git client (`git clone https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview.git`)

* Start Microsoft Visual Studio and select `Open a project or solution` under `Get started`.

* Navigate to the folder containing this sample, and select the solution file `image-analysis-quickstart.sln`.

* Follow [the instructions here](/docs/common/local-nuget-feed.md) to set up a local NuGet feed using Visual Studio, so the Azure AI Vision SDK NuGet package can be used when you compile the sample.

* Edit the `image-analysis-quickstart.cpp` source:
  * Replace the string `YourComputerVisionEndpoint` with your computer vision endpoint.
  * Replace the string `YourComputerVisionKey` with your own computer vision key.

* Make sure `x64` is selected for `Solution platforms` (`Debug` or `Release`).

* Press `F6`, or select `Build` > `Build Solution`.

You should see the resulting executable `image-analysis-quickstart.exe` in the output folder `x64\Debug` (or `x64\Release`).

## Run the sample

* Open a command prompt windows in the output folder where the executable was created

* Make sure that the image file `laptop-on-kitchen-table.jpg` is in the folder (it should have been copied into this folder by Visual Studio when you compiled the sample)

* Run the sample without arguments by typing `image-analysis-quickstart.exe`

* You should see screen output similar to the following:

```txt
Please wait for image analysis results...
Descriptions:
        "a person sitting at a table with a laptop and cell phone", Confidence 0.5061
Objects:
        "kitchen appliance", Bounding box {X=730,Y=66,Width=135,Height=85}, Confidence 0.501
        "computer keyboard", Bounding box {X=523,Y=377,Width=185,Height=46}, Confidence 0.51
        "Laptop", Bounding box {X=471,Y=218,Width=289,Height=226}, Confidence 0.85
        "person", Bounding box {X=654,Y=0,Width=584,Height=473}, Confidence 0.855
```

## Troubleshooting

* If the computer vision key you entered is incorrect, you will see failed analysis result with an error message containing `Access denied due to invalid subscription key or wrong API endpoint`. Make sure you correctly copied the key from your Azure portal.

* If the computer vision endpoint is incorrect, you will see a failed analysis result with an error message containing `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`. Make sure you correctly copied the endpoint from your Azure portal.

* If the image file analyzed is not present in the output folder, this exception message will be shown: `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`. Copy the image file to the output folder and re-run.

## Required libraries for run-time distribution

The output folder contains many DLL files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```txt
Azure-AI-Vision-Core.dll
Azure-AI-Vision-Extension-Image.dll
Azure-AI-Vision-Input-File.dll
turbojpeg.dll
Vision_Core.dll
Vision_Media.dll
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C++ (TBD)
