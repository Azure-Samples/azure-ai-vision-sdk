# Image Analysis Samples (C# .NET Core, Linux x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` feature, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Linux x64 machine running Ubuntu 18.04/20.04/22.04, Debian 9/10/11 or Red Hat Enterprise Linux (RHEL) 7/8

* [.NET Core 3.1](https://dotnet.microsoft.com/download/dotnet/3.1) or higher installed

* On Ubuntu or Debian, run the following commands for the installation of required packages:

  ```sh
  sudo apt-get update
  sudo apt-get install libssl-dev
  ```

* On Ubuntu 22.04 LTS it is also required to download and install the latest `libssl1.1` package e.g. from [here](http://security.ubuntu.com/ubuntu/pool/main/o/openssl).

## Compile the sample

* **By compiling this sample you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Download the content of this repository to your Linux device. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Open a terminal window in the folder `samples/csharp/image-analysis/dotnetcore` containing the project solution `image-analysis-samples.csproj`

* Compile the sample by running:
  ```
  dotnet build
  ```

* When the build succeeds, you should see these resulting files:
  * `bin/Debug/netcoreapp3.1/image-analysis-samples.dll` - The .NET core executable of this sample.
  * `bin/Debug/netcoreapp3.1/Azure-AI-Vision-*.dll` - The SDK's C# binaries
  * `bin/Debug/netcoreapp3.1/runtimes/linux-x64/native` with files `libAzure-AI-Vision-*.so` and others - The SDK's native binaries

## Get usage help

To get usage help run with the `-h` or `--help` flag:
```
dotnet bin/Debug/netcoreapp3.1/image-analysis-samples.dll -h
```

You will see the following output:
```
 Azure AI Vision SDK - Image Analysis Samples

 To run the samples:

   dotnet image-analysis-samples.dll [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]

 Where:
   <your-key> - A computer vision key you get from your Azure portal.
     It should be a 32-character HEX number.
   <your-endpoint> - A computer vision endpoint you get from your Azure portal.
     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com

 As an alternative to specifying the above command line arguments, you can specify
 these environment variables: VISION_KEY and/or VISION_ENDPOINT.

 To get this usage help, run:

   dotnet image-analysis-samples.dll --help|-h
```

## Run the samples

* Make sure the image files `sample*.jpg` are in the folder.

* Run the sample in one of two ways:
  * By specifying the vision key & endpoint as run-time arguments:
  ```
  dotnet bin/Debug/netcoreapp3.1/image-analysis-samples.dll -k <your-key> -e <your-endpoint>
  ```
  * By first defining the appropriate environment variables, then running the executable without arguments:
  ```
  export VISION_KEY=<your-key>
  export VISION_ENDPOINT=<your-endpoint>

  dotnet bin/Debug/netcoreapp3.1/image-analysis-samples.dll
  ```

* You should see a menu of samples to run. Enter the number corresponding to the sample you want to run. If this is your first time, start with sample 1, as it does analysis of all the visual features. The sample will run and display the results in the console window. The menu will be displayed again, so you can run another sample. Select `0` to exit the program.

## Troubleshooting

An error message will be displayed if the sample fails to run. Here are some common errors and how to fix them:

* `401: Access denied due to invalid subscription key or wrong API endpoint. Make sure to provide a valid key for an active subscription and use a correct regional API endpoint for your resource`.
  * The computer vision key you entered may be incorrect. Make sure you correctly copied the key from your Azure portal. It should be a 32-character HEX number (no dashes).

* `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`.
  * Your endpoint may be incorrect. Make sure you correctly copied the endpoint from your Azure portal. It should be in the form `https://<your-computer-vision-resource-name>.cognitiveservices.azure.com`

* `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`
  * The image file cannot be found. Make sure you copy the image file to the folder where the executable is located, and re-run.

* `InvalidRequest: The feature 'Caption' is not supported in this region`
  * Your endpoint is from an Azure region that does not support the `Caption` and `DenseCaptions` features. You can either change the endpoint to a supported region, or remove the `Caption` and `DenseCaptions` features from the list of features to analyze.

## Required libraries for run-time distribution

The output folder contains many .so files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```
Azure.AI.Vision.Common.dll
Azure.AI.Vision.ImageAnalysis.dll
runtimes\linux-x64\native\Azure-AI-Vision-Native.so
runtimes\linux-x64\native\Azure-AI-Vision-Extension-Image.so
runtimes\linux-x64\native\Azure-AI-Vision-Input-File.so
runtimes\linux-x64\native\Vision_Core.so
runtimes\linux-x64\native\Vision_Media.so
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C# (TBD)
