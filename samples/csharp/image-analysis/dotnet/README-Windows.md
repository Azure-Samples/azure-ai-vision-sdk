# Image Analysis Samples (C# .NET Core, Windows x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` or `Dense Captions` features, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Windows 10 (or higher) x64 PC. We only support x64 platforms at the moment.

* [Microsoft Visual C++ Redistributable for Visual Studio 2015, 2017, 2019, and 2022](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist) for your platform.

* [.NET Runtime 6.0](https://dotnet.microsoft.com/download/dotnet/6.0) installed.

* If you want to compile the samples using Visual Studio (you have the option to compile using .NET Core CLI instead):
  * [Microsoft Visual Studio 2019](https://www.visualstudio.com/), Community Edition or higher.
  * The .NET Core cross-platform development workload in Visual Studio. You can enable it in `Tools` \> `Get Tools and Features`.

* if you do not want to compile the sample using Visual Studio, install [.NET 6.0 SDK](https://dotnet.microsoft.com/download/dotnet/6.0) or higher.

## Compile the sample using Visual Studio

* **By compiling these samples you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Start Microsoft Visual Studio and select `Open a project or solution` under `Get started`.

* Navigate to the folder containing these C# samples, and select the solution file `image-analysis-samples.sln`.

* Press `F6`, or select `Build` \> `Build Solution` to compile the sample.

You should see the resulting executables `image-analysis-samples.exe` and `image-analysis-samples.dll` in the output folder `\bin\Debug\netcoreapp3.1` (or `\bin\Release\netcoreapp3.1`).

## Get usage help

To get usage help, open a command prompt window in the output folder where the executable was created, and run it with the `-h` or `--help` flag:
```
image-analysis-samples.exe -h
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

* Open a command prompt window in the output folder where the executable was created

* Make sure that the image file `sample.jpg` is in the folder (it should have been copied into this folder by Visual Studio when you compiled the sample)

* Run the sample in one of two ways:
  * By specifying the vision key & endpoint as run-time arguments:
  ```
  image-analysis-samples.exe -k <your-key> -e <your-endpoint>
  ```
  * By first defining the appropriate environment variables, then running the executable without arguments:
  ```
  set VISION_KEY=<your-key>
  set VISION_ENDPOINT=<your-endpoint>

  image-analysis-samples.exe
  ```

* You should see a menu of samples to run. Enter the number corresponding to the sample you want to run. If this is your first time, start with sample 1, as it does analysis of all the visual features. The sample will run and display the results in the console window. The menu will be displayed again, so you can run another sample. Select `0` to exit the program.

## Alternative: Compile and run using .NET Core CLI

* Open a command prompt in the folder `samples\csharp\image-analysis\dotnet` where the project file `image-analysis-samples.csproj` is located.

* Make sure you have the .NET CLI installed (run `dotnet.exe` without arguments). It is installed as part of .NET Core.

* The compile:
    ```cmd
    dotnet build
    ```

* To run:
    ```cmd
    dotnet run
    ```

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

The output folder contains many DLL files needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```
Azure.AI.Vision.Common.dll
Azure.AI.Vision.ImageAnalysis.dll
runtimes\win-x64\native\Azure-AI-Vision-Native.dll
runtimes\win-x64\native\Azure-AI-Vision-Extension-Image.dll
runtimes\win-x64\native\Azure-AI-Vision-Input-Device.dll
```

## Additional resources

* [Quickstart article on learn.microsoft](https://learn.microsoft.com/azure/cognitive-services/computer-vision/quickstarts-sdk/image-analysis-client-library-40?tabs=visual-studio%2Cwindows&pivots=programming-language-csharp)
* [How-to guide on learn.microsoft](https://learn.microsoft.com/azure/cognitive-services/computer-vision/how-to/call-analyze-image-40?tabs=csharp)
* [Vision SDK API reference for C#](https://learn.microsoft.com/dotnet/api/azure.ai.vision.imageanalysis?view=azure-dotnet-preview)


