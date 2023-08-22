# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
#
# This code is integrated into this public document:
# https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-python#get-results-using-custom-model

import os
import azure.ai.vision as sdk

service_options = sdk.VisionServiceOptions(os.environ["VISION_ENDPOINT"],
                                           os.environ["VISION_KEY"])

vision_source = sdk.VisionSource(
    url="https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png")

# <model_name>
analysis_options = sdk.ImageAnalysisOptions()

analysis_options.model_name = "MyCustomModelName"
# </model_name>

# <analyze>
image_analyzer = sdk.ImageAnalyzer(service_options, vision_source, analysis_options)

result = image_analyzer.analyze()

if result.reason == sdk.ImageAnalysisResultReason.ANALYZED:

    if result.custom_objects is not None:
        print(" Custom Objects:")
        for object in result.custom_objects:
            print("   '{}', {} Confidence: {:.4f}".format(object.name, object.bounding_box, object.confidence))

    if result.custom_tags is not None:
        print(" Custom Tags:")
        for tag in result.custom_tags:
            print("   '{}', Confidence {:.4f}".format(tag.name, tag.confidence))

else:

    error_details = sdk.ImageAnalysisErrorDetails.from_result(result)
    print(" Analysis failed.")
    print("   Error reason: {}".format(error_details.reason))
    print("   Error code: {}".format(error_details.error_code))
    print("   Error message: {}".format(error_details.message))
# </analyze>
