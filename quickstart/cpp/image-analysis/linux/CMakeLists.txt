#
# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
#
# Azure AI Vision SDK -- C++ Image Analysis Quickstart for Linux
#

cmake_minimum_required(VERSION 3.10)

project(sample VERSION 1.0)

set(CMAKE_CXX_STANDARD_REQUIRED True)

# Set to the library and include folders of the Vision SDK
set(VISIONSDK_LIB_PATH /usr/lib/azure-ai-vision)
set(VISIONSDK_INC_PATH /usr/include/azure-ai-vision)

# Check that Vision SDK library path is configured correctly
find_library(AZURE_AI_VISION_CORE libAzure-AI-Vision-Core.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)

add_executable(
    sample
    ../image-analysis-quickstart.cpp
)

target_link_libraries(
    sample
  PRIVATE
    ${AZURE_AI_VISION_CORE}
)

target_include_directories(
    sample
  PRIVATE
    ${VISIONSDK_INC_PATH}
)
