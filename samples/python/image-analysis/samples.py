# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
"""
Azure AI Vision SDK -- Python Image Analysis Samples
"""
import time
import secrets

try:
    import azure.ai.vision as visionsdk
except ImportError:
    print("""
    Importing Azure AI Vision SDK for Python failed.
    Refer to README.md in this directory for installation instructions.
    """)
    import sys
    sys.exit(1)


def get_all_results():
    """
    This sample does analysis on an image file using all visual features
    and prints the results to the console, including the detailed results.
    """

    service_options = visionsdk.VisionServiceOptions(secrets.endpoint, secrets.key)

    # Specify the image file on disk to analyze. sample1.jpg is a good example to show most features,
    # except Text (OCR). Use sample2.jpg for OCR.
    vision_source = visionsdk.VisionSource(filename='sample1.jpg')

    # Or, instead of the above, specify a publicly accessible image URL to analyze. For example:
    # image_url = 'https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg'
    # vision_source = visionsdk.VisionSource(url=image_url)

    # Set the language and one or more visual features as analysis options
    image_analysis_options = visionsdk.ImageAnalysisOptions()

    # Mandatory. You must set one or more features to analyze. Here we use the full set of features.
    # Note that 'Captions' is only supported in Azure GPU regions (East US, France Central, Korea Central,
    # North Europe, Southeast Asia, West Europe, West US)
    image_analysis_options.features = (
        visionsdk.ImageAnalysisFeature.CROP_SUGGESTIONS |
        visionsdk.ImageAnalysisFeature.CAPTIONS |
        visionsdk.ImageAnalysisFeature.OBJECTS |
        visionsdk.ImageAnalysisFeature.PEOPLE |
        visionsdk.ImageAnalysisFeature.TEXT |
        visionsdk.ImageAnalysisFeature.TAGS
    )

    # Optional, and only relevant when you select ImageAnalysisFeature.CROP_SUGGESTIONS.
    # Define one or more aspect ratios for the desired cropping. Each aspect ratio needs to be in the range [0.75, 1.8].
    # If you do not set this, the service will return one crop suggestion with the aspect ratio it sees fit.
    image_analysis_options.cropping_aspect_ratios = [0.9, 1.33]

    # Optional. Default is "en" for English. See https://aka.ms/cv-languages for a list of supported
    # language codes and which visual features are supported for each language.
    image_analysis_options.language = 'en'

    # Optional. Default is "latest".
    image_analysis_options.model_version = 'latest'

    # Optional, and only relevant when you select ImageAnalysisFeature::Captions.
    # Set this to "true" to get gender neutral captions (the default is "false").
    image_analysis_options.gender_neutral_captions = True

    # Create the image analyzer object
    image_analyzer = visionsdk.ImageAnalyzer(service_options=service_options,
                                            vision_source=vision_source,
                                            image_analysis_options=image_analysis_options)

    # Do image analysis for the specified visual features
    print()
    print(' Please wait for image analysis results...')
    print()

    # This call creates the network connection and blocks until Image Analysis results
    # return (or an error occurred). Note that there is also an asynchronous (non-blocking)
    # version of this method: image_analyzer.analyze_async().
    result = image_analyzer.analyze()

    # Checks result.
    if result.reason == visionsdk.ImageAnalysisResultReason.ANALYZED:

        if result.captions is not None:
            print(' Captions:')
            for caption in result.captions:
                print('   \'{}\', Confidence {:.4f}'.format(caption.content, caption.confidence))

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

    elif result.reason == visionsdk.ImageAnalysisResultReason.ERROR:

        error_details = visionsdk.ImageAnalysisErrorDetails.from_result(result)
        print(" Analysis failed.")
        print("   Error reason: {}".format(error_details.reason))
        print("   Error code: {}".format(error_details.error_code))
        print("   Error message: {}".format(error_details.message))
        print(" Did you set the computer vision endpoint and key?")


def get_results_using_analyzed_event():
    """
    This sample does analysis on an image URL, showing how to use the
    Analyzed event to get the analysis result for one visual feature.
    """

    service_options = visionsdk.VisionServiceOptions(secrets.endpoint, secrets.key)

    image_url = 'https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg'
    vision_source = visionsdk.VisionSource(url=image_url)

    image_analysis_options = visionsdk.ImageAnalysisOptions()
    image_analysis_options.features = ( visionsdk.ImageAnalysisFeature.TAGS )

    image_analyzer = visionsdk.ImageAnalyzer(service_options=service_options,
                                            vision_source=vision_source,
                                            image_analysis_options=image_analysis_options)

    callback_done = False
    def analyzed_callback(args: visionsdk.ImageAnalysisEventArgs):
        """callback that signals analysis is done or has stopped for some reason"""
        if args.result.reason == visionsdk.ImageAnalysisResultReason.ANALYZED:

            if args.result.tags is not None:
                print(' Tags:')
                for tag in args.result.tags:
                    print('   \'{}\', Confidence {:.4f}'.format(tag.name, tag.confidence))

        elif args.result.reason == visionsdk.ImageAnalysisResultReason.ERROR:
            error_details = visionsdk.ImageAnalysisErrorDetails.from_result(args.result)
            print(" Analysis failed.")
            print("   Error reason: {}".format(error_details.reason))
            print("   Error code: {}".format(error_details.error_code))
            print("   Error message: {}".format(error_details.message))
            print(" Did you set the computer vision endpoint and key?")

        nonlocal callback_done
        callback_done = True

    image_analyzer.analyzed.connect(analyzed_callback)

    print()
    print(' Please wait for image analysis results...')
    print()
    result = image_analyzer.analyze()

    while not callback_done:
        time.sleep(.1)
