# Quickstart: Image Analysis (C++, Linux x64)

This sample demonstrates how to run Image Analysis on an image file on disk or an image URL, using Microsoft's Azure C++ Vision SDK for Linux.

Note that the Image Analysis Vision SDK is in private preview. APIs are subject to change. The SDK is not yet available to the public.

Only Linux x64 version is supported at the moment, via a Debian Vision SDK package.

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click `Go to resource`.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service. You'll paste your key and endpoint into the sample code as described below.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.

* A Linux x64 PC, running Ubuntu 18.04/20.04/22.04, Debian 9/10/11, Red Hat Enterprise Linux (RHEL) 7/8, or CentOS 7/8.

* On Ubuntu or Debian, install these packages to build and run this sample:

  ```sh
  sudo apt-get update
  sudo apt-get install build-essential libssl-dev wget
  ```

  * On **Ubuntu 22.04 LTS** it is also required to download and install the latest **libssl1.1** package from [here](http://security.ubuntu.com/ubuntu/pool/main/o/openssl)

## Install the Vision SDK from a Debian package (.deb)

1. **By extracting the Azure AI Vision SDK package you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license202012)**.

1. Downloaded the Debian package from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases/tag/0.7.0-beta.0.30318788) of the repository. It is named `azure-ai-vision-0.7.0-beta.0.1-Linux.deb`

1. Extract the Debian package (replace package name as needed):

   ```sh
   sudo apt install ./azure-ai-vision-0.7.0-beta.0.1-Linux.deb -y
   ```

   When asked `Do you wish to also install the Spatial Analysis Configuration UI (set RTCV_INSTALL_SPATIAL_ANALYSIS_GUI_TOOL=(0|1) to automate)?` type `n` for "no".

1. Verify installation succeeded by listing these folders:

   ```sh
   ls -la /usr/lib/azure-ai-vision
   ls -la /usr/include/azure-ai-vision
   ```

   You should see shared objects files named `libAzure-AI-Vision-*.so`, `libVision_*.so` and a few others in the first folder.

   You should see header files named `azac_api_*.h`, `vsion_api_*.h` and `azac_*.h` in the second folder.

## Compile the sample

* Download the content of this repository to your development PC. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/archive/master.zip), or cloning this repository using a Git client (`git clone https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview.git`)

* Navigate to the `linux` folder of this sample, where this `README.md` file is located

* Edit the `image-analysis-quickstart.cpp` source:
  * Replace the string `YourComputerVisionEndpoint` with your computer vision endpoint.
  * Replace the string `YourComputerVisionKey` with your own computer vision key.

* Create a `build` folder under the `linux` folder, where the executable will be built, and navigate to that folder:

```sh
mkdir build 
cd build
```

* A Makefile is provided for direct compilation using the `make` command. Alternatively, a `CMakeLists.txt` is also provided if you prefer to compile the sample using the `cmake` command:

  * To compile using make, run `make -f ../Makefile`.
  * To compile using CMake, run `cmake ..`, then run `make`.

You should see the resulting executable `sample` in the current folder.

## Run the sample

* To run the sample, you'll first need to configure the loader's library path to point to the folder where Vision SDK shared object are located (Note: this is likely a packaging bug. Once fixed, you will not need to do this step):

    ```sh
    export LD_LIBRARY_PATH=/usr/lib/azure-ai-vision
    ```

* You will also need to copy the image file `laptop-on-kitchen-table.jpg` to the current folder, such that it resides in the same folder as the executable `sample`:

    ```sh
    cp ../../laptop-on-kitchen-table.jpg . 
    ```

Now run the application:

```sh
./sample
```

You should see screen output similar to the following:

```txt
Please wait for image analysis results...
Descriptions:
    "a person sitting at a table with a laptop and cell phone", Confidence 0.5061
Objects:
    "kitchen appliance", Bounding box {x=730, y=66, w=135, h=85}, Confidence 0.501
    "computer keyboard", Bounding box {x=523, y=377, w=185, h=46}, Confidence 0.51
    "Laptop", Bounding box {x=471, y=218, w=289, h=226}, Confidence 0.85
    "person", Bounding box {x=654, y=0, w=584, h=473}, Confidence 0.855
```

## Troubleshooting

* If you forgot to run the `export LD_LIBRARY_PATH=/usr/lib/azure-ai-vision` command, you will get this error when trying to compile the sample:
`./sample: error while loading shared libraries: libAzure-AI-Vision-Core-Native.so: cannot open shared object file: No such file or directory`

* If the computer vision key you entered is incorrect, you will see failed analysis result with an error message containing `Access denied due to invalid subscription key or wrong API endpoint`. Make sure you correctly copied the key from your Azure portal.

* If the computer vision endpoint is incorrect, you will see a failed analysis result with an error message containing `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`. Make sure you correctly copied the endpoint from your Azure portal.

* If the image file analyzed is not present in the output folder, this exception message will be shown: `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`. Copy the image file to the output folder and re-run.

## Cleanup

The Vision SDK Debian package can be removed by running:

```sh
sudo apt autoremove azure-ai-vision
```

## Required libraries for run-time distribution

The folder `/usr/lib/azure-ai-vision` contains several shared object libraries (`.so` files), needed to support different sets of Vision SDK APIs. For Image Analysis, only the following subset is needed when you distribute a run-time package of your application:

```txt
libAzure-AI-Vision-Core.so
libAzure-AI-Vision-Input-File.so
libAzure-AI-Vision-Extension-Image.so
libVision_Core.so
libVision_Media.so
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Vision SDK API reference for C++ (TBD)
