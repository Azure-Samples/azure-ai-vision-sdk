# Image Analysis Samples (Java JRE, Windows x64 & Linux x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK for Java for Windows and Linux.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` or `Dense Captions` features, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Windows 10 (or higher) x64 PC. We only support x64 platforms at the moment. 

* Or alternatively, a Linux x64 machine running Ubuntu 18.04/20.04/22.04, Debian 9/10/11 or Red Hat Enterprise Linux (RHEL) 7/8. On **Ubuntu 22.04 LTS** it is also required to install **libssl** version 1.1 from source code. See instructions [here](../../../docs/ubuntu2204-notes.md).

* [Microsoft Visual C++ Redistributable for Visual Studio 2015, 2017, 2019, and 2022](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist) for your platform.

* Java 8 or newer JDK. Check with `java -version` on the command line.
  * **Note:** Make sure that the Java installation is native to the system architecture (e.g. Linux `x64`) and not running through emulation.

* If using Eclipse (or a similar development environment): [Eclipse IDE for Java developers](https://www.eclipse.org/downloads/packages/), version 2021-12 or newer.

* If using the command line: [Maven](https://maven.apache.org/) installed. On Linux, install from the distribution repositories if available. Check with `mvn -version` on the command line.

## Compile the sample using command line

* **By compiling these samples you will download the Azure AI Vision SDK. By doing so you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Navigate to `samples\java\image-analysis` folder and run `mvn clean package` command

* You should see the resulting jar executable containing all the required dependencies in `target\image-analysis-samples-snapshot-jar-with-dependencies.jar`.

## Get usage help

To get usage help, open a command prompt window in the folder `samples\java\image-analysis`, and run the sample with the `-h` or `--help` flag:
```
java -jar target\image-analysis-samples-snapshot-jar-with-dependencies.jar -h
```

You will see the following output:
```
Azure AI Vision SDK - Image Analysis Samples

To run the samples:

   java -jar ./target/image-analysis-samples-snapshot-jar-with-dependencies.jar [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]

Where:
   <your-key> - A computer vision key you get from your Azure portal.
     It should be a 32-character HEX number.
   <your-endpoint> - A computer vision endpoint you get from your Azure portal.
     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com

As an alternative to specifying the command line arguments, you can define
these environment variables: VISION_KEY and/or VISION_ENDPOINT.

To get this usage help, run:

   java -jar ./target/image-analysis-samples-snapshot-jar-with-dependencies.jar --help|-h
```

## Run the samples

* Open a command prompt in the folder `samples\java\image-analysis`

* Make sure that the image file `sample.jpg` is in the folder (it is included with this Java sample application)

* Run the sample in one of two ways:
  * By specifying the vision key & endpoint as run-time arguments:
  ```
  java -jar target\image-analysis-samples-snapshot-jar-with-dependencies.jar -k <your-key> -e <your-endpoint>
  ```
  * Or by first defining the appropriate environment variables:

  In Windows:
  ```
  set VISION_KEY=<your-key>
  set VISION_ENDPOINT=<your-endpoint>
  ```

  In Linux:

  ```
  export VISION_KEY=<your-key>
  export VISION_ENDPOINT=<your-endpoint>
  ```
  * And running the sample without arguments
  ```
  java -jar target\image-analysis-samples-snapshot-jar-with-dependencies.jar
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

## Additional resources

* [Quickstart article on learn.microsoft](https://learn.microsoft.com/azure/ai-services/computer-vision/quickstarts-sdk/image-analysis-client-library-40?tabs=visual-studio%2Cwindows&pivots=programming-language-java)
* [How-to guide on learn.microsoft](https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?tabs=java)
* [Vision SDK API reference for Java](https://learn.microsoft.com/java/api/com.azure.ai.vision.imageanalysis)
