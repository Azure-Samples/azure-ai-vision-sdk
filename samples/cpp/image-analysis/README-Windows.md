# Image Analysis Samples (C++, Windows x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` feature, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Windows 10 (or higher) x64 PC. We only support x64 platforms at the moment.

* [Microsoft Visual Studio 2019](https://visualstudio.microsoft.com/), Community Edition or higher.

* [Microsoft Visual C++ Redistributable for Visual Studio 2015, 2017, 2019, and 2022](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist) for your platform.

* The Desktop development with C++ workload in Visual Studio and the NuGet package manager component in Visual Studio. You can enable both in `Tools` > `Get Tools and Features`, under the `Workloads` and `Individual components` tabs, respectively.

## Compile the samples

* **By compiling these samples you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Start Microsoft Visual Studio and select `Open a project or solution` under `Get started`.

* Navigate to the folder containing these C++ samples, and select the solution file `image-analysis-samples.sln`.

* Make sure `x64` is selected for `Solution platforms` (`Debug` or `Release`).

* Press `F6`, or select `Build` > `Build Solution`.

You should see the resulting executable `image-analysis-samples.exe` in the output folder `x64\Debug` (or `x64\Release`).

## Get usage help

To get usage help, open a command prompt window in the output folder where the executable was created, and run it with the `-h` or `--help` flag:
```
image-analysis-samples.exe -h
```

You will see the following output:
```
 Azure AI Vision SDK - Image Analysis Samples

 To run the samples:

   image-analysis-samples.exe [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]

 Where:
   <your-key> - A computer vision key you get from your Azure portal.
     It should be a 32-character HEX number.
   <your-endpoint> - A computer vision endpoint you get from your Azure portal.
     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com

 As an alternative to specifying the above command line arguments, you can specify
 these environment variables: VISION_KEY and VISION_ENDPOINT

 To get this usage help, run:

   image-analysis-samples.exe --help|-h
```

## Run the samples

* Open a command prompt window in the output folder where the executable was created

* Make sure that the image files `sample*.jpg` are in the folder (they should have been copied into this folder by Visual Studio when you compiled the sample)

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

* You should see a menu of samples to run. Enter the number corresponding to the sample you want to run, and press `Enter`. If this is your first time, start with sample 1, as it does analysis of all the visual features. The sample will run and display the results in the console window. The menu will be displayed again, so you can run another sample. Select `0` to exit the program.

## Troubleshooting

An error message will be displayed if the sample fails to run. Here are some common errors and how to fix them:

* `401: Access denied due to invalid subscription key or wrong API endpoint. Make sure to provide a valid key for an active subscription and use a correct regional API endpoint for your resource`.
  * The computer vision key you entered may be incorrect. Make sure you correctly copied the key from your Azure portal. It should be a 32-character HEX number (no dashes).

* `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`.
  * Your endpoint may be incorrect. Make sure you correctly copied the endpoint from your Azure portal. It should be in the form `https://<your-computer-vision-resource-name>.cognitiveservices.azure.com`

* `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`
  * The image file cannot be found. Make sure you copy the image file to the folder where the executable is located, and re-run.

* `InvalidRequest: The feature 'Caption' is not supported in this region`
  * Your endpoint is from an Azure region that does not support the `Caption` feature. You can either change the endpoint to a supported region, or remove the `Caption` feature from the list of features to analyze.

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
