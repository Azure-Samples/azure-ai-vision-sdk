---
author: adinatru
ms.service: cognitive-services
ms.topic: include
ms.date: 03/05/2021
ms.author: adinatru
ms.custom: references_regions
---

# Get started with Face Identification

In this quickstart, you learn basic design patterns for Face Identification using the Cognitive Services Vision SDK, including:

* Face Identification


## Skip to samples on GitHub

If you want to skip straight to sample code, see the [C++ samples](https://github.com/Azure-Samples/cognitive-services-vision-sdk/blob/main/samples/cpp/linux/Face%20Identification/sample.cpp) on GitHub...


## Prerequisites

| Required | Purpose |
| ----------- | ----------- |
| Docker Engine | For C++ on Linux, a docker container is provided with the local pre-processing AI that calls the cloud for matching. To run this container, you need to install the Docker Engine on a [host computer / device](https://docs.microsoft.com/en-us/azure/cognitive-services/face/face-how-to-install-containers#the-host-computer). Docker provides packages that configure the Docker environment on [macOS](https://docs.docker.com/docker-for-mac/), [Windows](https://docs.docker.com/docker-for-windows/), and [Linux](https://docs.docker.com/engine/install/). For a primer on Docker and container basics, see the [Docker overview](https://docs.docker.com/get-started/overview/).<br />Docker must be configured to allow the containers to connect with and send billing data to Azure.<br />On Windows, Docker also must be configured to support Linux containers. |
| Familiarity |  You need a basic understanding of Docker concepts, such as registries, repositories, containers, and container images. You also need knowledge of basic docker commands. |
| Face resource | To use the SDK, you must have:<br/> <br/> An Azure **Face** resource and the associated API key and the endpoint URI. Both values are available on the **Overview** and **Keys** pages for the resource. They're required to start the container.<br/> <br/> **{API_KEY}**: One of the two available resource keys on the **Keys** page<br/> <br/> **{ENDPOINT_URI}**: The endpoint as provided on the **Overview** page |

## Install the Cognitive Services Vision SDK

Before you can do anything, you'll need to install the Cognitive Services Vision SDK. Currently, only Linux is supported at this time.

* <a href="https://docs.microsoft.com/azure/cognitive-services/speech-service/quickstarts/setup-platform?tabs=linux&pivots=programming-language-cpp" target="_blank">Linux <span class="docon docon-navigate-external x-hidden-focus"></span></a>
[comment]: <> "TODO: fix link above"

## Download the Docker Container

The container can be started by pulling the image from the repository and executing a `docker run` command.

### Azure Container Registry Login

Login to the Azure Container Registry (ACR) using an assigned token (replace [user] and [token] with yours below).

```shell
docker login pacofficial.azurecr.io -u [user] -p [token]
```

### Download the container image

The docker pull command will download the container image from the registry.

```shell
docker pull pacofficial.azurecr.io/facepipeline:0.0.1-amd64
```

### Verify the image is on the workstation

The docker image command will display terminal output to confirm local availability.

```shell
docker image ls
REPOSITORY                           TAG          IMAGE ID      CREATED     SIZE
Pacofficial.azurecr.io/facepipeline  0.0.1-amd64  a55453970c0a  - week ago  666MB
```

### Start the container from the image

The docker run command will generate the container from the image, making it available for configuration and interaction.

```shell
docker run -it --rm -d --ipc=host --net=host -p 50051:50051 -p 5000:5000 -e EULA="accept" **a55453970c0a**
```

Note that the command output will display: "WARNING Published ports are discarded when using host network mode", but you can disregard this warning.

## **Use the FaceIdentityContainerClient** 

A convenience class is provided to get started quickly, called FaceIdentityContainerClient. To make use of it, first include the header in which it’s defined:

```c++
#include <FaceIdentityContainerClient/FaceIdentityContainerClient.h>
```

While you’re at it, there are some helpers for GUIDs (globally unique identifiers) that you may want to also include:

```c++
#include <faceapi/guid.hpp>
```

You probably want to define callback functions; one provides feedback about faces identified (or not) as well as other events, while another callback indicates if an attempt to connect succeeded (or not):

```c++
void MessageReceivedCallback(void * userData,
                             face_identity::FaceIdentityResults message)
{
    static const char* state[] {"Idle", "Identifying"};
 
    switch (message.message_type())
    {
        case 0: // State change (face_identities will be empty)
            fprintf(stderr, "Going from state %s to %s\n",
 	              state[message.old_state()], state[message.new_state()]);
            break;
        case 1: // Failed to Identify (face_identities has 1 face and
                // most_likely_candidate will not be set)
            fprintf(stderr, "Failed to identify face");
            break;
        case 2: // Successful Identify (Faces in face_identities identified)
            fprintf(stderr, "Identified faces:\n");
            for(int i = 0; i < message.face_identities_size(); i++)
            {
                Guid guid;
                std::memcpy(&guid, message.face_identities().at(i)
                            .uuid().c_str(), sizeof(Guid));
                std::string faceUuid = GuidToString(guid);
 
                std::memcpy(&guid, message.face_identities().at(i)
                            .most_likely_candidate()
                            .uuid().c_str(), sizeof(Guid));
                std::string personUuid = GuidToString(guid);
 
                fprintf(stderr, "\tFace %s was identified as person %s with"
  				    " confidence %lf\n", faceUuid.c_str(),
                    personUuid.c_str(),
                    message.face_identities().at(i)
                    .most_likely_candidate().confidence());
            }
 
            break;
        case 3: // Frame processing rate on service side; indicates
                // that the service will drop unprocessed frames
            fprintf(stderr, "Frame rate = %lf\n", message.frame_rate());
            break;
        case 4: // Progress of identification for a face
            fprintf(stderr, "Attempt %d of %d\n",
                message.attempt_message().attempt(),
                message.attempt_message().max_attempt());
            break;
    }
}

enum ConnectStatus {Unconnected, Connected, Errored};

void ConnectionStatusChangedCallback(void* userData, bool connected)
{
    fprintf(stderr, "Connection status changed to %s\n", 
            connected ? "connected" : "errored");
    *reinterpret_cast<ConnectStatus*>(userData) = 
        connected ? ConnectStatus::Connected : ConnectStatus::Errored;
}

```

## **Create a Face Configuration**

To call the Face service using the Cognitive Services Vision SDK through a docker container, you need to create a JSON configuration, which has a variety of parameters. Be sure to update the  `FaceApi_EndPoint`, `SubscriptionKey`, `FaceApi_IrPersonGroup`, and `FaceApi_ColorPersonGroup` parameters. (The endpoint will be a URL for the service, such as https://pace2ewus.cognitiveservices.azure.com/.)  This json configuration may be loaded from a file, created dynamically, or a static string.

```json
{
"location": null,
"skillId": "faceId",
"version": 1.0,
"enabled": true,
"platformLogLevel": "info",
"nodesLogLevel": "info",
"parameters": {
        "FaceApi_EndPoint": "<<<YOUR ENDPOINT HERE>>>",
        "FaceApi_SubscriptionKey": "<<<YOUR KEY HERE>>>",
        "FaceApi_ColorPersonGroup": "<<<YOUR COLOR PERSON GROUP HERE>>>",
        "FaceApi_IrPersonGroup": "<<<YOUR IR PERSON GROUP HERE>>>",
        "FaceApi_ColorRecogModel": "recognition_04",
        "FaceApi_IrRecogModel": "ir_recognition_02",
        "FaceApi_IsLargePersonGroup": 1,
        "FaceApi_Threshold": 0.6,
        "Proxy_Url": "",
        "Proxy_Port": 0,
        "Proxy_Username": "",
        "Proxy_Password": "",
        "Proxy_DisableCertificateValidation": 1,
        "Control_MaxAttempts": 0,
        "Control_MaxTime": 2000,
        "Control_ColorImageTargetHeight": 540,
        "Control_IrImageTargetHeight": 540,
        "Control_LeftCameraParameters":
             "1,0,0,0,1,0,0,0,1,0,0,0,955.9531,550.1998,1246.4091,1245.1992",
        "Control_RightCameraParameters":
 "0.99997,-0.0035347,-0.0073588,0.0035375,0.99999,0.00036803,0.0073575,-0.00039405,0.99997,0.05779,-0.000070524,-0.00021861,964.8345,569.6853,1241.4576,1240.7482",
        "Control_EnableAntiSpoofing": 0,
        "Control_OnlyIdLargestFace": 1,
        "Filter_MinimumArea": 10000,
        "Filter_ExposureMin": 1,
        "Filter_ExposureMax": 205,
        "Filter_FrontalThreshold": 0.125,
        "Filter_PoseRoll": 90,
        "Filter_PosePitch": 25,
        "Filter_PoseYaw": 20,
        "DeviceId": "scrubkey",
        "CustomerId": "develop",
        "DisableDataCollection": 0,
    }
}
```

## Create a FaceIdentityContainerClient instance

To create an instance of the FaceIdentityContainerClient class, call the factory function: 

```c++
std::shared_ptr<FaceIdentityContainerClient> client =
    FaceIdentityContainerClient::Create(nullptr, // optional host
    &MessageReceivedCallback, &ConnectionStatusChangedCallback,
    &connectionStatus); // optional user data pointer passed to callbacks

```

## **Connect to the container**

Note that the connectionStatus is just an variable to keep track of our state:

```c++
enum ConnectStatus connectionStatus = ConnectStatus::Unconnected;
```

When we are ready to connect to the container and know our camera source kind (infrared/color), pixel format (RGB, RGBA, Y8, Y16), width, height, stride, we call:

```c++
if (FAILED(client->StartConnect(configString.c_str(),
    FaceIdentityContainerClient::SOURCE_KIND_INFRARED,
    FaceIdentityContainerClient::PIXEL_FORMAT_RGB, 
    width, height, stride)))
{
    fprintf(stderr, "Error starting connection to container\n");
    return 1;
}
```

The `StartConnect` function is asynchronous, and any `ConnectionChangedCallback` we registered in the factory will be called when it succeeds or fails. For simplicity here, we’ll just poll in a sleeping loop (though a condition variable might be best in your own code):

```c++
while (connectionStatus == ConnectStatus::Unconnected)
{
    std::this_thread::sleep_for(std::chrono::milliseconds(33));
}
if (connectionStatus != ConnectStatus::Connected)
{
    fprintf(stderr, "Error connecting to container\n");
    return 1;
}
```

## Sending frames

Assuming we have a video image, taken from an IR or color camera, with (uint8_t*) scan0 pointing to the pixels, we can send a frame to be processed with:

```c++
if (FAILED(client->SendFrame(scan0, 
  timestampCount * FramePeriodInMS)))
{
  fprintf(stderr, "Error sending frame\n");
  return 1;
}
```

Subsequent frames may be sent in the same fashion (a delay may be needed between frames if they are pre-captured to prevent skipping frames).

Your MessageReceivedCallback will be called asynchronously as the face identification results arrive.

## **Next steps**

- Feel free to make changes to the FaceIdentityContainerClient code; it is just a starting point for you.
- See the Face [reference documentation](https://docs.microsoft.com/en-us/azure/cognitive-services/face//) for detail on classes and functions.

## **Feedback**

