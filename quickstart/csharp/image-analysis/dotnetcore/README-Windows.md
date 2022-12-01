# Quickstart: Image Analysis (C# .NET Core, Windows x64)

This sample demonstrates how to run Image Analysis on an image file on disk or an image URL, using Microsoft's C# Azure AI Vision SDK for Windows.

Note that the Image Analysis Vision SDK is in private preview. APIs are subject to change. The SDK is not yet available to the public.

Only Windows 10 (or higher) x64 is supported at the moment, via a NuGet Vision SDK package.

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service. You'll paste your key and endpoint into the sample code as described below.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.

* A Windows 10 (or higher) x64 PC

* [Microsoft Visual Studio 2019](https://www.visualstudio.com/), Community Edition or higher.

* The .NET Core cross-platform development workload in Visual Studio. You can enable it in `Tools` \> `Get Tools and Features`.

* [Microsoft Visual C++ Redistributable for Visual Studio 2015, 2017 and 2019](https://support.microsoft.com/help/2977003/the-latest-supported-visual-c-downloads) for your platform.
    
* [.NET Core 3.1](https://dotnet.microsoft.com/download/dotnet/3.1) or higher installed

## Compile the sample using Visual Studio

* **By compiling this sample you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license202012)**.

* Downloaded the Azure AI Vision SDK NuGet file from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases/tag/0.7.0-beta.0.30318788) of the repository. It is named `Azure.AI.Vision.0.7.0-beta.0.30318788.nupkg`

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/archive/master.zip), or cloning this repository using a Git client (`git clone https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview.git`)

* Start Microsoft Visual Studio and select `File` \> `Open` \> `Project/Solution`.

* Navigate to the folder containing this sample, and select the solution file `ImageAnalysisQuickstart.sln`.

* Follow [the instructions here](/docs/common/local-nuget-feed.md) to set up a local NuGet feed using Visual Studio, so the Azure AI Vision SDK NuGet package can be used when you compile the sample.

* Edit the `Program.cs` source:
  * Replace the string `YourComputerVisionEndpoint` with your computer vision endpoint.
  * Replace the string `YourComputerVisionKey` with your own computer vision key.

* Press `F6`, or select `Build` \> `Build Solution` to compile the sample.

## Run the sample

To debug the app and then run it, press F5 or use `Debug` \> `Start Debugging`.

To run the app without debugging, press Ctrl+F5 or use `Debug` \> `Start Without Debugging`.

To run the app from a command prompt, open a command prompt window and navigate to the output folder `bin\Debug\netcoreapp3.1`. Run `ImageAnalysisQuickstart.exe` without arguments.

You should see screen output similar to the following:

```txt
Please wait for image analysis results...
Descriptions:
        "a person sitting at a table with a laptop and cell phone", Confidence 0.506
Objects:
        "kitchen appliance", Bounding box {X=730,Y=66,Width=135,Height=85}, Confidence 0.501
        "computer keyboard", Bounding box {X=523,Y=377,Width=185,Height=46}, Confidence 0.510
        "Laptop", Bounding box {X=471,Y=218,Width=289,Height=226}, Confidence 0.850
        "person", Bounding box {X=654,Y=0,Width=584,Height=473}, Confidence 0.855
```

## Alternative: Compile and run using .NET Core CLI

* Open a command prompt in the folder `quickstart\csharp\image-analysis\dotnetcore` where the project file `ImageAnalysisQuickstart.csproj` is located. 

* Make sure you have the .NET CLI installed (run `dotnet.exe`). It is installed as part of .NET Core.

* Follow [the instructions here](/docs/common/local-nuget-feed.md#using-net-core-cli-windows-linux-macos) to set up a local NuGet feed using .NET CLI, so the Azure AI Vision SDK NuGet package can be used when you compile the sample.

* The compile:
    ```cmd
    dotnet build ImageAnalysisQuickstart.csproj
    ```

* To run:
    ```cmd
    dotnet run bin\Debug\netcoreapp3.1\ImageAnalysisQuickstart.dll
    ```
  or:
    ```cmd
    bin\Debug\netcoreapp3.1\ImageAnalysisQuickstart.exe
    ```

## Troubleshooting

* If you did not set a local NuGet feed source properly, you will get a build error similar to: `error NU1101: Unable to find package Azure.AI.Vision. No packages exist with this id in source(s): Microsoft Visual Studio Offline Packages, nuget.org`.  Follow [the instructions here](/docs/common/local-nuget-feed.md) to set up a local NuGet feed using Visual Studio Package Manager UI, Package Manager console or .NET CLI, so the Azure AI Vision SDK NuGet file you downloaded can be found.

* If the computer vision key you entered is incorrect, you will see failed analysis result with an error message containing `Access denied due to invalid subscription key or wrong API endpoint`. Make sure you correctly copied the key from your Azure portal.

* If the computer vision endpoint is incorrect, you will see a failed analysis result with an error message containing `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`. Make sure you correctly copied the endpoint from your Azure portal.

* If the image file analyzed is not present in the output folder, this exception message will be shown: `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`. Copy the image file to the output folder and re-run.

## Required libraries for run-time distribution

The output folder contains many DLL files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```txt
Azure-AI-Vision-CSharp.dll
runtimes\win-x64\native\Azure-AI-Vision-Core.dll
runtimes\win-x64\native\Azure-AI-Vision-Extension-Image.dll
runtimes\win-x64\native\Azure-AI-Vision-Input-File.dll
runtimes\win-x64\native\Vision_Core.dll
runtimes\win-x64\native\Vision_Media.dll
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C# (TBD)
