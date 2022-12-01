//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Quickstart
//
// <doc-tag-cpp-image-analysis-quickstart>
#include <memory>
#include <iostream>
#include <vision_api_cxx.h>

using namespace Azure::AI::Vision::Service;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::ImageAnalysis;

void AnalyzeImage()
{
    // Replace the string "YourComputerVisionEndpoint" with your Computer Vision endpoint, found in the Azure portal.
    // The endpoint has the form "https://<your-computer-vision-resource-name>.cognitiveservices.azure.com".
    // Replace the string "YourComputerVisionKey" with your Computer Vision key. The key is a 32-character
    // HEX number (no dashes), found in the Azure portal. Similar to "d0dbd4c2a93346f18c785a426da83e15".
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint("YourComputerVisionEndpoint", "YourComputerVisionKey");

    // Specify the image file on disk to analyze
    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("laptop-on-kitchen-table.jpg");

    // Or, instead of the above, specify a publicly accessible image URL to analyze
    // (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
    //std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("YourImageURL");

    // Creates the options object that will control the ImageAnalyzer
    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    // You must define one or more features to extract during image analysis
    analysisOptions->SetFeatures({ImageAnalysisFeature::Descriptions, ImageAnalysisFeature::Objects});

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::cout << " Please wait for image analysis results...\n\n";
    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        const Nullable<ContentDescriptions>& descriptions = result->GetDescriptions();
        if (descriptions.HasValue())
        {
            std::cout << " Descriptions:" << std::endl;
            for (const ContentDescription& description : descriptions.Value())
            {
                std::cout << "   \"" << description.Content << "\", ";
                std::cout << "Confidence " << description.Confidence << std::endl;
            }
        }

        const Nullable<DetectedObjects>& objects = result->GetObjects();
        if (objects.HasValue())
        {
            std::cout << " Objects:" << std::endl;
            for (const DetectedObject& object : objects.Value())
            {
                std::cout << "   \"" << object.Name << "\", ";
                std::cout << "Bounding box " << object.BoundingBox.ToString();
                std::cout << ", Confidence " << object.Confidence << std::endl;
            }
        }
    }
    else if (result->GetReason() == ImageAnalysisResultReason::Stopped)
    {
        std::cout << " Analysis failed." << std::endl;
        std::shared_ptr<ImageAnalysisStopDetails> stopDetails = ImageAnalysisStopDetails::FromResult(result);

        if (stopDetails->GetReason() == ImageAnalysisStopReason::Error)
        {
            std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
            std::cout << "   Error reason =  " << (int)errorDetails->GetReason() << std::endl;
            std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
            std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
        }
    }
}

int main()
{
    try
    {
        AnalyzeImage();
    }
    catch (std::exception e)
    {
        std::cout << e.what();
    }

    return 0;
}
// </doc-tag-cpp-image-analysis-quickstart>
