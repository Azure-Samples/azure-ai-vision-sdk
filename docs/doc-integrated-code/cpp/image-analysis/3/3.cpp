//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// <snippet-single>
#include <vision_api_cxx_image_analyzer.h>

using namespace Azure::AI::Vision::ImageAnalysis;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::Service;

void AnalyzeImage()
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint("PASTE_YOUR_COMPUTER_VISION_ENDPOINT_HERE", "PASTE_YOUR_COMPUTER_VISION_SUBSCRIPTION_KEY_HERE");

    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("sample1.jpg");

    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetModelName("MyCustomModelName");

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        const Nullable<DetectedObjects>& objects = result->GetCustomObjects();
        if (objects.HasValue())
        {
            std::cout << " Custom objects:" << std::endl;
            for (const DetectedObject& object : objects.Value())
            {
                std::cout << "   \"" << object.Name << "\", ";
                std::cout << "Bounding box " << object.BoundingBox.ToString();
                std::cout << ", Confidence " << object.Confidence << std::endl;
            }
        }

        const Nullable<ContentTags>& tags = result->GetCustomTags();
        if (tags.HasValue())
        {
            std::cout << " Custom tags:" << std::endl;
            for (const ContentTag& tag : tags.Value())
            {
                std::cout << "   \"" << tag.Name << "\"";
                std::cout << ", Confidence " << tag.Confidence << std::endl;
            }
        }
    }
    else if (result->GetReason() == ImageAnalysisResultReason::Error)
    {
        std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
        std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
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
// </snippet-single>
