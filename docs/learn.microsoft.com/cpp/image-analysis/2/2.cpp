//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// <snippet-single>
#include <vision_api_cxx_image_analyzer.h>

using namespace Azure::AI::Vision::ImageAnalysis;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::Service;

std::string PolygonToString(std::vector<int32_t> boundingPolygon);

void AnalyzeImage()
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint("PASTE_YOUR_COMPUTER_VISION_ENDPOINT_HERE", "PASTE_YOUR_COMPUTER_VISION_SUBSCRIPTION_KEY_HERE");

    //std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg");
    std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("https://csspeechstorage.blob.core.windows.net/drop/TestData/images/ocr-sample.jpg");

    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetFeatures(
        {
            ImageAnalysisFeature::Caption,
            ImageAnalysisFeature::Text,
        });

    analysisOptions->SetLanguage("en");

    analysisOptions->SetGenderNeutralCaption(true);

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        std::cout << " Image height = " << result->GetImageHeight().Value() << std::endl;
        std::cout << " Image width = " << result->GetImageWidth().Value() << std::endl;
        std::cout << " Model version = " << result->GetModelVersion().Value() << std::endl;

        const Nullable<ContentCaption>& caption = result->GetCaption();
        if (caption.HasValue())
        {
            std::cout << " Caption:" << std::endl;
            std::cout << "   \"" << caption.Value().Content << "\", Confidence " << caption.Value().Confidence << std::endl;
        }

        const Nullable<DetectedText>& detectedText = result->GetText();
        if (detectedText.HasValue())
        {
            std::cout << " Text:\n";
            for (const DetectedTextLine& line : detectedText.Value().Lines)
            {
                std::cout << "   Line: \"" << line.Content << "\"";
                std::cout << ", Bounding polygon " << PolygonToString(line.BoundingPolygon) << std::endl;

                for (const DetectedTextWord& word : line.Words)
                {
                    std::cout << "     Word: \"" << word.Content << "\"";
                    std::cout << ", Bounding polygon " << PolygonToString(word.BoundingPolygon);
                    std::cout << ", Confidence " << word.Confidence << std::endl;
                }
            }
        }

        std::shared_ptr<ImageAnalysisResultDetails> resultDetails = ImageAnalysisResultDetails::FromResult(result);
        std::cout << " Result details:\n";;
        std::cout << "   Image ID = " << resultDetails->GetImageId() << std::endl;
        std::cout << "   Result ID = " << resultDetails->GetResultId() << std::endl;
        std::cout << "   Connection URL = " << resultDetails->GetConnectionUrl() << std::endl;
        std::cout << "   JSON result = " << resultDetails->GetJsonResult() << std::endl;
    }
    else if (result->GetReason() == ImageAnalysisResultReason::Error)
    {
        std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
    }
}

std::string PolygonToString(std::vector<int32_t> boundingPolygon)
{
    std::string out = "{";
    for (int i = 0; i < boundingPolygon.size(); i += 2)
    {
        out += ((i == 0) ? "{" : ",{") +
            std::to_string(boundingPolygon[i]) + "," +
            std::to_string(boundingPolygon[i + 1]) + "}";
    }
    out += "}";
    return out;
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
