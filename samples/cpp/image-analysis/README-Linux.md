# Image Analysis Samples (C++, Linux x64)

These samples demonstrate how to run Image Analysis on an image file on disk or an image URL, using Microsoft's new Azure AI Vision SDK.

**Note that the Vision SDK is in public preview. APIs are subject to change.**

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.
  * Note that in order to run Image Analysis with the `Caption` feature, the Azure resource needs to be from one of the following GPU-supported regions: East US, France Central, Korea Central, North Europe, Southeast Asia, West Europe, West US.

* A Linux x64 device, running Ubuntu 18.04/20.04/22.04, Debian 9/10/11 or Red Hat Enterprise Linux (RHEL) 7/8.

* On Ubuntu or Debian, install these packages to build and run this sample:

  ```sh
  sudo apt-get update
  sudo apt-get install build-essential libssl-dev wget
  ```

  * On **Ubuntu 22.04 LTS** it is also required to download and install the latest **libssl1.1** package from [here](http://security.ubuntu.com/ubuntu/pool/main/o/openssl)

## Install the Vision SDK from a Debian package (.deb)

1. **By extracting the Azure AI Vision SDK package you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license)**.

1. Downloaded the following five Debian packages from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk/releases/tag/0.8.0-alpha.0.33370873) of the repository, and place them in the same folder on your device:
   * `azure-ai-vision-runtime-core-0.8.0-alpha.0.1-Linux.deb`
   * `azure-ai-vision-runtime-core-media-0.8.0-alpha.0.1-Linux.deb`
   * `azure-ai-vision-runtime-image-analysis-0.8.0-alpha.0.1-Linux.deb`
   * `azure-ai-vision-dev-core-0.8.0-alpha.0.1-Linux.deb`
   * `azure-ai-vision-dev-image-analysis-0.8.0-alpha.0.1-Linux.deb`

1. Install the debian packages by running the two command below, in the following order (Note: once the packages are published, you will only need to install one package, and that will automatically install all dependent packages):

   ```
   sudo apt install ./azure-ai-vision-runtime-*.deb -y -f
   sudo apt install ./azure-ai-vision-dev-*.deb -y -f
   ```

1. Verify installation succeeded by listing these folders:

   ```
   ls -la /usr/lib/azure-ai-vision
   ls -la /usr/include/azure-ai-vision
   ls -la /usr/share/doc/azure-ai-vision-*
   ```

   You should see shared object files named `libAzure-AI-Vision-*.so` and a few others in the first folder.

   You should see header files named `vsion_api_cxx_*.h` and others in the second folder.

   You should see package documents in the /usr/share/doc/azure-ai-vision-* folders (LICENSE.md, REDIST.txt, ThirdPartyNotices.txt).

## Compile the sample

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk/archive/master.zip), or cloning this repository using a Git client: `git clone https://github.com/Azure-Samples/azure-ai-vision-sdk.git`

* Navigate to the `linux` folder of this sample, where this `README.md` file is located

* Create a `build` folder under the `linux` folder, where the executable will be built, and navigate to that folder:

```sh
mkdir build 
cd build
```

* A Makefile is provided for direct compilation using the `make` command. Alternatively, a `CMakeLists.txt` is also provided if you prefer to compile the sample using the `cmake` command:

  * To compile using make, run `make -f ../makefile`.
  * To compile using CMake, run `cmake ..`, then run `make`.

You should see the resulting executable `image-analysis-samples.exe` in the current folder.

## Get usage help

To get usage help run the executable with the `-h` or `--help` flag:
```
./image-analysis-samples.exe -h
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
 these environment variables: COMPUTER_VISION_KEY and COMPUTER_VISION_ENDPOINT

 To get this usage help, run:

   image-analysis-samples.exe --help|-h
```

## Run the sample

* Open a terminal windows where the executable `image-analysis-samples.exe` is located.

* Copy the image files `sample*.jpg` to the current folder, such that it resides in the same folder as the executable `image-analysis-samples.exe`:
    ```
    cp ../../sample*.jpg .
    ```
    
* Run the sample in one of two ways:
  * By specifying the vision key & endpoint as run-time arguments:
  ```
  ./image-analysis-samples.exe -k <your-key> -e <your-endpoint>
  ```
  * By first defining the appropriate environment variables, then running the executable without arguments:
  ```
  export COMPUTER_VISION_KEY=<your-key>
  export COMPUTER_VISION_ENDPOINT=<your-endpoint>

  ./image-analysis-samples.exe
  ```

* You should see a menu of samples to run. Enter the number corresponding to the sample you want to run, and press `Enter`. If this is your first time, start with sample 1, as it does analysis of all the visual features. The sample will run and display the results in the console window. The menu will be displayed again, so you can run another sample. Select `0` to exit the program.

## Troubleshooting

An error message will be displayed if the sample fails to run. Here are some common errors and how to fix them:

* `error while loading shared libraries: libAzure-AI-Vision-Extension-Image.so: cannot open shared object file: No such file or directory`
  * To fix this, run `export LD_LIBRARY_PATH=/usr/lib/azure-ai-vision/:$LD_LIBRARY_PATH` and re-run `./image-analysis-samples.exe`

* `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`.
  * Your endpoint may be incorrect. Make sure you correctly copied the endpoint from your Azure portal. It should be in the form `https://<your-computer-vision-resource-name>.cognitiveservices.azure.com`

* `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING) `
  * The image file cannot be found. Make sure you copy the image file to the folder where the executable is located, and re-run.

* `InvalidRequest: The feature 'Caption' is not supported in this region`
  * Your endpoint is from an Azure region that does not support the `Caption` feature. You can either change the endpoint to a supported region, or remove the `Caption` feature from the list of features to analyze.

## Cleanup

The Vision SDK Debian packages can be removed by running this single command:

```
 sudo apt-get purge azure-ai-vision-*
```

## Required libraries for run-time distribution

The folder `/usr/lib/azure-ai-vision` contains several shared object libraries (`.so` files), needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```
libAzure-AI-Vision-Core.so
libAzure-AI-Vision-Input-File.so
libAzure-AI-Vision-Extension-Image.so
libVision_Core.so
libVision_Media.so
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C++ (TBD)
