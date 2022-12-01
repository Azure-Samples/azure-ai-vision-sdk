# Quickstart: Image Analysis (C# .NET Core, Linux x64)

This sample demonstrates how to run Image Analysis on an image file on disk or an image URL, using Microsoft's C# Azure AI Vision SDK for Linux.

Note that the Image Analysis Vision SDK is in private preview. APIs are subject to change. The SDK is not yet available to the public.

Only Linux x64 versions are supported at the moment, via a NuGet Vision SDK package.

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service. You'll paste your key and endpoint into the sample code as described below.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.

* A Linux x64 machine running Ubuntu 18.04/20.04/22.04, Debian 9/10/11, Red Hat Enterprise Linux (RHEL) 7/8, or CentOS 7/8

* [.NET Core 3.1](https://dotnet.microsoft.com/download/dotnet/3.1) or higher installed

* On Ubuntu or Debian, run the following commands for the installation of required packages:

  ```sh
  sudo apt-get update
  sudo apt-get install libssl-dev
  ```

* On Ubuntu 22.04 LTS it is also required to download and install the latest `libssl1.1` package e.g. from [here](http://security.ubuntu.com/ubuntu/pool/main/o/openssl).

## Compile the sample

* **By compiling this sample you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license202012)**.

* Downloaded the Azure AI Vision SDK NuGet file from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases/tag/0.7.0-beta.0.30318788) of the repository. It is named `Azure.AI.Vision.0.7.0-beta.0.30318788.nupkg`

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/archive/master.zip), or cloning this repository using a Git client (`git clone https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview.git`)

* Edit the `Program.cs` source:
  * Replace the string `YourComputerVisionEndpoint` with your computer vision endpoint.
  * Replace the string `YourComputerVisionKey` with your own computer vision key.

* Open a command prompt in the folder `quickstart/csharp/image-analysis/dotnetcore` containing the project solution `ImageAnalysisQuickstart.csproj`

* Follow [the instructions here](/docs/common/local-nuget-feed.md#using-net-core-cli-windows-linux-macos) to set up a local NuGet feed using .NET Core CLI, so the Azure AI Vision SDK NuGet package can be used when you compile the sample.
* Compile the sample by running:
  ```bash
  dotnet build ImageAnalysisQuickstart.csproj
  ```
* You should see these resulting files:
  * `bin/Debug/netcoreapp3.1/ImageAnalysisQuickstart.dll` - The .NET core executable of the sample.  
  * `bin/Debug/netcoreapp3.1/Azure-AI-Vision-csharp.dll` - The SDK's C# binary
  * `bin/Debug/netcoreapp3.1/runtimes/linux-x64/native` with files `libAzure-AI-Vision-*.so` and others - The SDK's native binaries

## Run the sample

Run the following:

```bash
dotnet run bin/Debug/netcoreapp3.1/ImageAnalysisQuickstart.dll
```

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

## Troubleshooting

* If the computer vision key you entered is incorrect, you will see failed analysis result with an error message containing `Access denied due to invalid subscription key or wrong API endpoint`. Make sure you correctly copied the key from your Azure portal.

* If the computer vision endpoint is incorrect, you will see a failed analysis result with an error message containing `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`. Make sure you correctly copied the endpoint from your Azure portal.

* If the image file analyzed is not present in the output folder, this exception message will be shown: `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`. Copy the image file to the output folder and re-run.

## Required libraries for run-time distribution

The output folder contains many .so files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```txt
Azure-AI-Vision-CSharp.dll
runtimes\linux-x64\native\libAzure-AI-Vision-Core.so
runtimes\linux-x64\native\libAzure-AI-Vision-Extension-Image.so
runtimes\linux-x64\native\libAzure-AI-Vision-Input-File.so
runtimes\linux-x64\native\libVision_Core.so
runtimes\linux-x64\native\libVision_Media.so
```
## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C# (TBD)
