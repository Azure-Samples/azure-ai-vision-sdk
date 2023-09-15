# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
#
# This code is integrated into this public document:
# https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/background-removal?tabs=python

import os

import azure.ai.vision as sdk

service_options = sdk.VisionServiceOptions(os.environ["VISION_ENDPOINT"],
                                           os.environ["VISION_KEY"])

vision_source = sdk.VisionSource(
    url="https://learn.microsoft.com/azure/ai-services/computer-vision/media/quickstarts/presentation.png")

# <segmentation_mode>
analysis_options = sdk.ImageAnalysisOptions()

analysis_options.segmentation_mode = sdk.ImageSegmentationMode.BACKGROUND_REMOVAL
# </segmentation_mode>

# <segment>
image_analyzer = sdk.ImageAnalyzer(service_options, vision_source, analysis_options)

result = image_analyzer.analyze()

if result.reason == sdk.ImageAnalysisResultReason.ANALYZED:

    image_buffer = result.segmentation_result.image_buffer
    print(" Segmentation result:")
    print("   Output image buffer size (bytes) = {}".format(len(image_buffer)))
    print("   Output image height = {}".format(result.segmentation_result.image_height))
    print("   Output image width = {}".format(result.segmentation_result.image_width))

    output_image_file = "output.png"
    with open(output_image_file, 'wb') as binary_file:
        binary_file.write(image_buffer)
    print("   File {} written to disk".format(output_image_file))

else:

    error_details = sdk.ImageAnalysisErrorDetails.from_result(result)
    print(" Analysis failed.")
    print("   Error reason: {}".format(error_details.reason))
    print("   Error code: {}".format(error_details.error_code))
    print("   Error message: {}".format(error_details.message))
    print(" Did you set the computer vision endpoint and key?")
# </segment>
