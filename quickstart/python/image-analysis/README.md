# Quickstart: Image Analysis (Python, Windows x64 & Linux x64)

This sample demonstrates how to run Image Analysis on an image file on disk or an image URL, using Azure AI Vision SDK for Python.

Note that the Azure AI Vision SDK is in private preview. APIs are subject to change. The SDK is not yet available to the public.

## Prerequisites

* An Azure subscription - [Create one for free](https://azure.microsoft.com/free/cognitive-services/).

* Once you have your Azure subscription, [create a Computer Vision resource](https://portal.azure.com/#create/Microsoft.CognitiveServicesComputerVision) in the Azure portal to get your key and endpoint. After it deploys, click **Go to resource**.

  * You will need the key and endpoint from the resource you create to connect your application to the Computer Vision service. You'll paste your key and endpoint into the sample code as described below.
  * You can use the free pricing tier (`F0`) to try the service, and upgrade later to a paid tier for production.

* On Windows and Linux Python 3.7 or later needs to be installed. Downloads are available [here](https://www.python.org/downloads/). Note that depending on your operating system, python version and alias set for its executable, the python executable may be `py`, `python` or `python3`. This document assumes the executable name is `python`.

* The Vision SDK for Python is available for Windows 10 (x64) and above, and Linux (x64) running Ubuntu 18.04/20.04/22.04, Debian 9/10/11, Red Hat Enterprise Linux (RHEL) 7/8, or CentOS 7/8.

* On Windows you need the [Microsoft Visual C++ Redistributable for Visual Studio 2017](https://support.microsoft.com/help/2977003/the-latest-supported-visual-c-downloads) for your platform.

* On Ubuntu or Debian, install these packages to build and run this sample:

  ```sh
  sudo apt-get update
  sudo apt-get install build-essential libssl-dev wget
  ```

  * On **Ubuntu 22.04 LTS** it is also required to download and install the latest **libssl1.1** package e.g. from http://security.ubuntu.com/ubuntu/pool/main/o/openssl/.

* On RHEL or CentOS, install these packages to build and run this sample:

  ```sh
  sudo yum update
  sudo yum groupinstall "Development tools"
  sudo yum install openssl wget
  ```

## Install the Vision SDK from a Python wheel (.whl)

* **By downloading the Python wheel you acknowledge the [Azure AI Vision SDK license agreement](https://aka.ms/azai/vision/license202012)**.

* Download the appropriate Python wheel (.whl) file from [this release](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/releases/tag/0.7.0-beta.0.30318788) of the repository.

  * Python wheels for Windows have this format (`XXXX` being the SDK version, and `YY` the targeted major and minor Python versions): `azure_ai_vision-XXXX-cpYY-cpYY-win_amd64.whl`.

  * Python wheels for Linux have this format: `azure_ai_vision-XXXX-py3-none-manylinux1_x86_64.whl`.

* Open a Windows command prompt / Linux terminal and install the Python wheel, by running the following (replace with your own python wheel file name):

    ```sh
    python -m pip install "azure_ai_vision-XXXX-py3-none-win_amd64.whl" --user --force-reinstall
    ```

    On Linux, if you get an error similar to `/usr/bin/python3: No module named pip`, run the following, and then re-run the above install command:
    ```sh
    curl https://bootstrap.pypa.io/pip/get-pip.py -o get-pip.py
    python get-pip.py
    ```

## Run the sample

* Download the content of this repository to your development PC / Linux machine. You can do that by either downloading and extracting this [ZIP file](https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview/archive/master.zip), or cloning this repository using a Git client (`git clone https://github.com/Azure-Samples/azure-ai-vision-sdk-private-preview.git`)

* Open a Windows command prompt / Linux terminal and navigate to the `quickstart\python\image-analysis` folder, where this `README.md` file is located.

* Edit the `quickstart.py` source file:
  * Replace the string `YourComputerVisionEndpoint` with your computer vision endpoint.
  * Replace the string `YourComputerVisionKey` with your own computer vision key.

* Run the sample by typing:

    ```sh
    python quickstart.py
    ```

* You should see screen output similar to the following:

    ```txt
     Descriptions:
       "a person sitting at a table with a laptop and cell phone", Confidence 0.5061
     Objects:
       "kitchen appliance", Rectangle(x=730, y=66, w=135, h=85), Confidence: 0.5010
       "computer keyboard", Rectangle(x=523, y=377, w=185, h=46), Confidence: 0.5100
       "Laptop", Rectangle(x=471, y=218, w=289, h=226), Confidence: 0.8500
       "person", Rectangle(x=654, y=0, w=584, h=473), Confidence: 0.8550
    ```

## Troubleshooting

* If you run the sample but did not install the Azure AI Vision SDK python wheel beforehand, you will see the error `ModuleNotFoundError: No module named 'azure.ai.vision'`.

* If the computer vision key you entered is incorrect, you will see failed analysis result with an error message containing `Access denied due to invalid subscription key or wrong API endpoint`. Make sure you correctly copied the key from your Azure portal.

* If the computer vision endpoint is incorrect, you will see a failed analysis result with an error message containing `Failed with error: HTTPAPI_OPEN_REQUEST_FAILED`. Make sure you correctly copied the endpoint from your Azure portal.

* If the image file analyzed is not present in the output folder, this exception message will be shown: `Exception with an error code: 0x73 (AZAC_ERR_FAILED_TO_OPEN_INPUT_FILE_FOR_READING)`. Copy the image file to the output folder and re-run.

## Cleanup

The Vision SDK Python package can be removed by running (replace with your own python wheel file name):

```sh
python -m pip uninstall "azure_ai_vision-XXXX-py3-none-win_amd64.whl"
```

## References

* Quickstart article on the SDK documentation site (TBD)
* Azure AI Vision SDK API reference for Python (TBD)

