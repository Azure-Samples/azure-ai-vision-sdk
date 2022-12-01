# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.

# Azure AI Vision SDK -- Python Image Analysis Quickstart

# <code>
import azure.ai.vision as visionsdk


# Replace the string "YourComputerVisionEndpoint" with your Computer Vision endpoint, found in the Azure portal.
# The endpoint has the form "https://<your-computer-vision-resource-name>.cognitiveservices.azure.com".
# Replace the string "YourComputerVisionKey" with your Computer Vision key. The key is a 32-character
# HEX number (no dashes), found in the Azure portal. Similar to "d0dbd4c2a93346f18c785a426da83e15".
computer_vision_endpoint, computer_vision_key = "YourComputerVisionEndpoint", "YourComputervisionKey"

service_options = visionsdk.VisionServiceOptions(endpoint=computer_vision_endpoint, key=computer_vision_key)

# Specify the image file on disk to analyze
image_file = 'laptop-on-kitchen-table.jpg'
vision_source = visionsdk.VisionSource(filename=image_file)

# Or, instead of the above, specify a publicly accessible image URL to analyze
# (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
# vision_source = visionsdk.VisionSource(url=image_url)

# Set one or more visual features as analysis options
image_analysis_options = visionsdk.ImageAnalysisOptions()
image_analysis_options.features = (
    visionsdk.ImageAnalysisFeature.DESCRIPTIONS | visionsdk.ImageAnalysisFeature.OBJECTS
)

# Create the image analyzer object
image_analyzer = visionsdk.ImageAnalyzer(service_options=service_options,
                                         vision_source=vision_source,
                                         image_analysis_options=image_analysis_options)

# Do image analysis for the specified visual features
print(" Please wait for image analysis results...\n")
result = image_analyzer.analyze()

# Checks result.
if result.reason == visionsdk.ImageAnalysisResultReason.ANALYZED:
    print(" Descriptions:")
    descriptions = result.descriptions
    for description in descriptions:
        print("   \"{}\", Confidence {:.4f}".format(description.content, description.confidence))
    objects = result.objects
    print(" Objects:")
    for object in objects:
        print("   \"{}\", {}, Confidence: {:.4f}".format(object.name, object.bounding_box, object.confidence))

elif result.reason == visionsdk.ImageAnalysisResultReason.STOPPED:
    error_details = visionsdk.ImageAnalysisErrorDetails(result)
    print("Analysis failed.")
    print("Error reason: {}".format(error_details.error_code))
    print("Error message: {}".format(error_details.message))
    print("Did you set the computer vision endpoint and key?")
# </code>
