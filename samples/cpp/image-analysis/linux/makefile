#
# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
#
# Azure AI Vision SDK - C++ Image Analysis Samples for Linux
#

# Set to the library and include folders of the Vision SDK
VISIONSDK_LIB_PATH:=/usr/lib/azure-ai-vision
VISIONSDK_INC_PATHS:=/usr/include/azure-ai-vision

# Check that Vision SDK library path is configured correctly
CHECK_FOR_VISION_SDK := $(shell test -f $(VISIONSDK_LIB_PATH)/libAzure-AI-Vision-Core.so && echo Success)
ifneq ("$(CHECK_FOR_VISION_SDK)","Success")
  $(error Please set VISIONSDK_LIB_PATH to point to your extracted Vision SDK, $$VISIONSDK_LIB_PATH/libAzure-AI-Vision-Core.so should exist.)
endif

LIBS:=-lAzure-AI-Vision-Extension-Image -lAzure-AI-Vision-Core -lAzure-AI-Vision-Input-File -lVision_Core -lVision_Media

RUNPATH="-Wl,-rpath=$(VISIONSDK_LIB_PATH)"

SRC=../../samples.cpp ../../secrets.cpp ../../stdafx.cpp ../../main.cpp

all: image-analysis-samples.exe

# Note: to run the sample after it compiled, you must set LD_LIBRARY_PATH to point to $VISIONSDK_LIB_PATH.
image-analysis-samples.exe:
	g++ $< -o $@ $(SRC)\
	    --std=c++14 \
	    $(patsubst %,-I%, $(VISIONSDK_INC_PATHS)) \
	    $(patsubst %,-L%, $(VISIONSDK_LIB_PATH)) \
	    $(LIBS) \
	    $(RUNPATH)

