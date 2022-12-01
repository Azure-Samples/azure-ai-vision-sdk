# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
"""
Azure AI Vision SDK -- Python Image Analysis Samples
"""
import secrets

try:
    import azure.ai.vision as visionsdk
except ImportError:
    print("""
    Importing the Vision SDK for Python failed.
    Refer to
    https://docs.microsoft.com/azure/cognitive-services/speech-service/quickstart-python for
    installation instructions.
    """)
    import sys
    sys.exit(1)


def get_all_results():
    """
    This sample does analysis on an image file using all visual features
    and prints the results to the console, including the detailed results.
    """

    service_options = visionsdk.VisionServiceOptions(secrets.endpoint, secrets.key)

    # Specify the image file on disk to analyze
    image_file = 'laptop-on-kitchen-table.jpg'
    vision_source = visionsdk.VisionSource(filename=image_file)

    # Or, instead of the above, specify a publicly accessible image URL to analyze. For example:
    # image_url = 'https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg'
    # vision_source = visionsdk.VisionSource(url=image_url)

    # Set the language and one or more visual features as analysis options
    image_analysis_options = visionsdk.ImageAnalysisOptions()

    image_analysis_options.features = (
        visionsdk.ImageAnalysisFeature.CROP_SUGGESTIONS |
        visionsdk.ImageAnalysisFeature.DESCRIPTIONS |
        visionsdk.ImageAnalysisFeature.OBJECTS |
        visionsdk.ImageAnalysisFeature.PEOPLE |
        visionsdk.ImageAnalysisFeature.TEXT |
        visionsdk.ImageAnalysisFeature.TAGS
    )

    # Optional, and only relevant when you selected ImageAnalysisFeature.CROP_SUGGESTIONS above.
    # Default ratio is 1.94. Each aspect ratio needs to be in the range [0.75, 1.8].
    image_analysis_options.cropping_aspect_ratios = [1.0, 1.33]

    # Optional. Default is 'en' for English.
    image_analysis_options.language = 'en'

    # Optional. Default is "latest".
    image_analysis_options.model_version = 'latest'

    # Create the image analyzer object
    image_analyzer = visionsdk.ImageAnalyzer(service_options=service_options,
                                            vision_source=vision_source,
                                            image_analysis_options=image_analysis_options)

    # Do image analysis for the specified visual features
    print()
    print(' Please wait for image analysis results...')
    print()
    result = image_analyzer.analyze()

    # Checks result.
    if result.reason == visionsdk.ImageAnalysisResultReason.ANALYZED:

        if result.descriptions is not None:
            print(' Descriptions:')
            for description in result.descriptions:
                print('   \'{}\', Confidence {:.4f}'.format(description.content, description.confidence))

        if result.objects is not None:
            print(' Objects:')
            for object in result.objects:
                print('   \'{}\', {} Confidence: {:.4f}'.format(object.name, object.bounding_box, object.confidence))

        if result.tags is not None:
            print(' Tags:')
            for tag in result.tags:
                print('   \'{}\', Confidence {:.4f}'.format(tag.name, tag.confidence))

        if result.people is not None:
            print(' People:')
            for person in result.people:
                print('   {}, Confidence {:.4f}'.format(person.bounding_box, person.confidence))

        if result.crop_suggestions is not None:
            print(' Crop Suggestions:')
            for crop_suggestion in result.crop_suggestions:
                print('   Aspect ratio {}: Crop suggestion {}'.format(crop_suggestion.aspect_ratio, crop_suggestion.bounding_box))

        if result.text is not None:
            print(' Text:')
            for line in result.text.lines:
                points_string = '{' + ', '.join([str(int(point)) for point in line.bounding_polygon]) + '}'
                print('   Line: \'{}\', Bounding polygon {}'.format(line.content, points_string))
                for word in line.words:
                    points_string = '{' + ', '.join([str(int(point)) for point in word.bounding_polygon]) + '}'
                    print('     Word: \'{}\', Bounding polygon {}, Confidence {:.4f}'.format(word.content, points_string, word.confidence))

        print(' Image Height: {}'.format(result.image_height))
        print(' Image Width: {}'.format(result.image_width))
        print(' Image ID: {}'.format(result.image_id))
        print(' Result ID: {}'.format(result.result_id))
        print(' Model Version: {}'.format(result.model_version))
        print(' JSON Result: {}'.format(result.json_result))

    elif result.reason == visionsdk.ImageAnalysisResultReason.STOPPED:
        error_details = visionsdk.ImageAnalysisErrorDetails(result)
        print("Analysis failed.")
        print("Error reason: {}".format(error_details.error_code))
        print("Error message: {}".format(error_details.message))
        print("Did you set the computer vision endpoint and key?")

def get_results_using_analyzed_event(endpoint, key):
    """
    This sample does analysis on an image URL, showing how to use the
    Analyzed event to get the analysis result for one visual feature.
    """
    pass

def using_frame_source(endpoint, key):
    """
    This sample does analysis on an image that is provided as a memory buffer.
    """
    pass
