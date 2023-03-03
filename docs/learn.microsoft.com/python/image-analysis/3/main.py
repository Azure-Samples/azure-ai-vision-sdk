# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.

# <snippet-single>
import azure.ai.vision as sdk

service_options = sdk.VisionServiceOptions("PASTE_YOUR_COMPUTER_VISION_ENDPOINT_HERE", "PASTE_YOUR_COMPUTER_VISION_SUBSCRIPTION_KEY_HERE")

vision_source = sdk.VisionSource(url="https://csspeechstorage.blob.core.windows.net/drop/TestData/images/ocr-sample.jpg")

analysis_options = sdk.ImageAnalysisOptions()

# Set your custom model name here
analysis_options.model_name = 'MyCustomModelName'

image_analyzer = sdk.ImageAnalyzer(service_options, vision_source, analysis_options)

result = image_analyzer.analyze()

if result.reason == sdk.ImageAnalysisResultReason.ANALYZED:

    if result.custom_objects is not None:
        print(' Custom Objects:')
        for object in result.custom_objects:
            print('   \'{}\', {} Confidence: {:.4f}'.format(object.name, object.bounding_box, object.confidence))

    if result.custom_tags is not None:
        print(' Custom Tags:')
        for tag in result.custom_tags:
            print('   \'{}\', Confidence {:.4f}'.format(tag.name, tag.confidence))

elif result.reason == sdk.ImageAnalysisResultReason.ERROR:

    error_details = sdk.ImageAnalysisErrorDetails.from_result(result)
    print(" Analysis failed.")
    print("   Error reason: {}".format(error_details.reason))
    print("   Error code: {}".format(error_details.error_code))
    print("   Error message: {}".format(error_details.message))
# <snippet-single>
