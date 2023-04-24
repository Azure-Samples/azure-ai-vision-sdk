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

# Check that Vision SDK binaries are found
find_library(AZURE_AI_VISION_CORE libAzure-AI-Vision-Native.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)
find_library(AZURE_AI_VISION_EXTENSION_IMAGE libAzure-AI-Vision-Extension-Image.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)
find_library(AZURE_AI_VISION_INPUT_FILE libAzure-AI-Vision-Input-File.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)
find_library(VISION_CORE libVision_Core.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)
find_library(VISION_MEDIA libVision_Media.so PATHS ${VISIONSDK_LIB_PATH} REQUIRED)

add_executable(
    image-analysis-samples.exe
    ../main.cpp
    ../secrets.cpp
    ../samples.cpp
    ../stdafx.cpp
)

target_link_libraries(
    image-analysis-samples.exe
  PRIVATE
    ${AZURE_AI_VISION_CORE}
    ${AZURE_AI_VISION_EXTENSION_IMAGE}
    ${AZURE_AI_VISION_INPUT_FILE}
    ${VISION_CORE}
    ${VISION_MEDIA}
)

target_include_directories(
    image-analysis-samples.exe
  PRIVATE
    ${VISIONSDK_INC_PATH}
)
