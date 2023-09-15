# Image Analysis Samples (Python, Windows x64 & Linux x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click **Go to resource**.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` or `Dense Captions` feature, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* On Windows and Linux Python 3.7 or later needs to be installed. Downloads are available [here](https://www.python.org/downloads/). Note that depending on your operating system, python version and alias set for its executable, the python executable may be `py`, `python` or `python3`. This document assumes the executable name is `python`.

* The Vision SDK for Python is available for Windows 10 (x64) and above, and Linux (x64) running Ubuntu 18.04/20.04/22.04, Debian 9/10/11 or Red Hat Enterprise Linux (RHEL) 7/8.

* On Windows you need the [Microsoft Visual C++ Redistributable packages for Visual Studio 2015, 2017, 2019, and 2022](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist) for your platform.

* On Ubuntu or Debian, install these packages to build and run this sample:

  ```sh
  sudo apt-get update
  sudo apt-get install build-essential libssl-dev wget
  ```

  * On **Ubuntu 22.04 LTS** it is also required to install **libssl** version 1.1 from source code. See instructions [here](../../../docs/ubuntu2204-notes.md).

## Install the Vision SDK

* **By installing the Python wheel you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

* Open a Windows command prompt / Linux terminal and install the Python wheel, by running the following:

    ```
    python -m pip install azure-ai-vision
    ```

    On Linux, if you get an error similar to `/usr/bin/python3: No module named pip`, run the following, and then re-run the above install command:

    ```
    curl https://bootstrap.pypa.io/pip/get-pip.py -o get-pip.py
    python get-pip.py
    ```

## Download the samples

Download the content of this repository to your development PC / Linux machine. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

## Get usage help

To get usage help, open a Windows command prompt / Linux terminal and navigate to the `samples\python\image-analysis` folder in the samples repository, where this `README.md` file is located. Then run the sample with the `-h` or `--help` flag:

```
python main.py -h
```

You will see the following output:

```
 Azure AI Vision SDK - Image Analysis Samples

 To run the samples:

   python main.py [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]

 Where:
   <your-key> - A computer vision key you get from your Azure portal.
     It should be a 32-character HEX number.
   <your-endpoint> - A computer vision endpoint you get from your Azure portal.
     It should have the form:
     https://<your-computer-vision-resource-name>.cognitiveservices.azure.com

 As an alternative to specifying the above command line arguments, you can specify
 these environment variables: VISION_KEY and/or VISION_ENDPOINT.

 To get this usage help, run:

   python main.py --help|-h
```

## Run the sample

* Open a Windows command prompt / Linux terminal and navigate to the `samples\python\image-analysis` folder, where this `README.md` file is located.

* Run the sample in one of two ways:

  * By specifying the vision key & endpoint as run-time arguments:
  ```
  python main.py -k <your-key> -e <your-endpoint>
  ```

  * Or by first defining the appropriate environment variables, then running the executable without arguments:

    * On Windows:
    ```
    set VISION_KEY=<your-key>
    set VISION_ENDPOINT=<your-endpoint>
    python main.py
    ```

    * On Linux:
    ```
    export VISION_KEY=<your-key>
    export VISION_ENDPOINT=<your-endpoint>
    python main.py
    ```

* You should see a menu of samples to run. Enter the number corresponding to the sample you want to run, and press `ENTER`. If this is your first time, start with the first sample, as it does analysis of all the visual features. The sample will run and display the results in the console window. The menu will be displayed again, so you can run another sample. Select `CTRL-Z` (on Windows) or `CTRL-D` (on Linux) to exit the program.

## Troubleshooting

An error message will be displayed if the sample fails to run. Here are some common errors and how to fix them:

* `Importing Azure AI Vision SDK for Python failed. Refer to README.md in this directory for installation instructions.`
  * The Vision SDK for Python is not installed. Make sure you followed the installation instructions above.

* `401: Access denied due to invalid subscription key or wrong API endpoint. Make sure to provide a valid key for an active subscription and use a correct regional API endpoint for your resource`.
  * The computer vision key you entered may be incorrect. Make sure you correctly copied the key from your Azure portal. It should be a 32-character HEX number (no dashes).

* `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`.
  * Your endpoint may be incorrect. Make sure you correctly copied the endpoint from your Azure portal. It should be in the form `https://<your-computer-vision-resource-name>.cognitiveservices.azure.com`

* `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`
  * The image file cannot be found. Make sure you copy the image file to the folder where the executable is located, and re-run.

* `InvalidRequest: The feature 'Caption' is not supported in this region`
  * Your endpoint is from an Azure region that does not support the `Caption` feature. You can either change the endpoint to a supported region, or remove the `Caption` feature from the list of features to analyze.

## Cleanup

The Vision SDK Python package can be removed by running (replace with your own python wheel package name):

```
python -m pip uninstall azure_ai_vision
```

## Additional resources

* [Quickstart article on learn.microsoft](https://learn.microsoft.com/azure/ai-services/computer-vision/quickstarts-sdk/image-analysis-client-library-40?tabs=visual-studio%2Cwindows&pivots=programming-language-python)
* [How-to guide on learn.microsoft](https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?tabs=python)
* [Vision SDK API reference for C#](https://learn.microsoft.com/python/api/azure-ai-vision)