# Image Analysis Samples (C# .NET UWP, Windows x64)

This is a sample Universal Windows Platform (UWP) application that demonstrates how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

It does not support Image Analysis using a cutom model. It also does not support Image Segmentation. However, the code can easly be updated to support both (See C# .NET Console sample for refrence).

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` or `Dense Captions` features, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Windows 10 (or higher) x64 PC. We only support x64 platforms at the moment.

* [Microsoft Visual C++ Redistributable for Visual Studio 2015, 2017, 2019, and 2022](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist) for your platform.

* [.NET Runtime 3.1](https://dotnet.microsoft.com/download/dotnet/3.1) installed.

* [Microsoft Visual Studio 2019](https://www.visualstudio.com/), Community Edition or higher, with the **Universal Windows Platform development** workload installed (You can enable it in **Tools** \> **Get Tools and Features**).

## Compile the sample

* **By compiling this sample you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Start Microsoft Visual Studio and select `Open a project or solution` under `Get started`.

* Navigate to the folder containing this C# UWP sample, and select the solution file `image-analysis-uwp.sln`.

* Press `F6`, or select `Build` \> `Build Solution` to compile the sample.

## Run the sample

To debug the application press F5 or use **Debug** \> **Start Debugging**. To run the application without debugging, press Ctrl+F5 or use **Debug** \> **Start Without Debugging**.

* In the text box **1. Enter Credentials**, enter your Computer Vision key and endpoint. Default values are read from environment variables
`VISION_KEY` and `VISION_ENDPOINT`. If they were defined, their values will show up here and no need to take further action here.

* In the text box **2. Select an Image to Analyze**, select either image file input or image URL input. The default is image URL. The image URL and image file values shown on the screen should both work if you do not want to provide your own image (the image file `sample.jpg` is included in this application package).

* In section **3. Select Analysis Options**, check or uncheck the boxes that define which analysis features you are interested in. As stated above, to use `Caption` or `Dense Captions`, your Azure resouce needs to be from a GPU-supported region, otherwise analysis will fail. You can also change the default result language, set one or more cropping aspect ration and select gender neutral captions.

* In section **4. Analyze the Image**, press `Click here to analyze` button.

If analysis succeeds, the results (as text output) will be show under section **5. See Results**. If failed, a failure message will be shown.

## Required libraries for run-time distribution

The output folder contains many DLL files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```
Azure.AI.Vision.Common.dll
Azure.AI.Vision.ImageAnalysis.dll
runtimes\win-x64\native\Azure-AI-Vision-Native.dll
runtimes\win-x64\native\Azure-AI-Vision-Extension-Image.dll
```

## Additional resources

* [Quickstart article on learn.microsoft](https://learn.microsoft.com/azure/cognitive-services/computer-vision/quickstarts-sdk/image-analysis-client-library-40?tabs=visual-studio%2Cwindows&pivots=programming-language-csharp)
* [How-to guide on learn.microsoft](https://learn.microsoft.com/azure/cognitive-services/computer-vision/how-to/call-analyze-image-40?tabs=csharp)
* [Vision SDK API reference for C#](https://learn.microsoft.com/dotnet/api/azure.ai.vision.imageanalysis?view=azure-dotnet-preview)


