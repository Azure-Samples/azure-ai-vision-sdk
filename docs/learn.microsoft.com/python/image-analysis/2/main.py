# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.

# <snippet-single>
import azure.ai.vision as sdk

service_options = sdk.VisionServiceOptions("PASTE_YOUR_COMPUTER_VISION_ENDPOINT_HERE", "PASTE_YOUR_COMPUTER_VISION_SUBSCRIPTION_KEY_HERE")

vision_source = sdk.VisionSource(url="https://csspeechstorage.blob.core.windows.net/drop/TestData/images/ocr-sample.jpg")

analysis_options = sdk.ImageAnalysisOptions()

analysis_options.features = (
    sdk.ImageAnalysisFeature.CAPTION |
    sdk.ImageAnalysisFeature.TEXT
)

analysis_options.language = 'en'

analysis_options.gender_neutral_caption = True

image_analyzer = sdk.ImageAnalyzer(service_options, vision_source, analysis_options)

result = image_analyzer.analyze()

if result.reason == sdk.ImageAnalysisResultReason.ANALYZED:

    print(' Image height: {}'.format(result.image_height))
    print(' Image width: {}'.format(result.image_width))
    print(' Model version: {}'.format(result.model_version))

    if result.caption is not None:
        print(' Caption:')
        print('   \'{}\', Confidence {:.4f}'.format(result.caption.content, result.caption.confidence))

    if result.text is not None:
        print(' Text:')
        for line in result.text.lines:
            points_string = '{' + ', '.join([str(int(point)) for point in line.bounding_polygon]) + '}'
            print('   Line: \'{}\', Bounding polygon {}'.format(line.content, points_string))
            for word in line.words:
                points_string = '{' + ', '.join([str(int(point)) for point in word.bounding_polygon]) + '}'
                print('     Word: \'{}\', Bounding polygon {}, Confidence {:.4f}'
                        .format(word.content, points_string, word.confidence))

    result_details = sdk.ImageAnalysisResultDetails.from_result(result)
    print(' Result details:')
    print('   Image ID: {}'.format(result_details.image_id))
    print('   Result ID: {}'.format(result_details.result_id))
    print('   Connection URL: {}'.format(result_details.connection_url))
    print('   JSON result: {}'.format(result_details.json_result))

elif result.reason == sdk.ImageAnalysisResultReason.ERROR:

    error_details = sdk.ImageAnalysisErrorDetails.from_result(result)
    print(" Analysis failed.")
    print("   Error reason: {}".format(error_details.reason))
    print("   Error code: {}".format(error_details.error_code))
    print("   Error message: {}".format(error_details.message))
# <snippet-single>
