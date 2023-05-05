// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/cognitive-services/computer-vision/how-to/call-analyze-image-40?tabs=cpp

// <snippet_single>
#include <vision_api_cxx_image_analyzer.h>

using namespace Azure::AI::Vision::ImageAnalysis;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::Service;

std::string PolygonToString(std::vector<int32_t> boundingPolygon);
std::string GetEnvironmentVariable(const std::string name);

void AnalyzeImage()
{
    // <vision_service_options>
    auto serviceOptions = VisionServiceOptions::FromEndpoint(
         GetEnvironmentVariable("VISION_ENDPOINT"),
         GetEnvironmentVariable("VISION_KEY"));
    // </vision_service_options>

    // <vision_source>
    auto imageSource = VisionSource::FromUrl(
        "https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png");
    // </vision_source>

    // <visual_features>
    auto analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetFeatures(
        {
            ImageAnalysisFeature::CropSuggestions,
            ImageAnalysisFeature::Caption,
            ImageAnalysisFeature::DenseCaptions,
            ImageAnalysisFeature::Objects,
            ImageAnalysisFeature::People,
            ImageAnalysisFeature::Text,
            ImageAnalysisFeature::Tags
        });
    // </visual_features>

    // <cropping_aspect_rations>
    analysisOptions->SetCroppingAspectRatios({ 0.9, 1.33 });
    // </cropping_aspect_rations>

    // <language>
    analysisOptions->SetLanguage("en");
    // </language>

    // <gender_neutral_caption>
    analysisOptions->SetGenderNeutralCaption(true);
    // </gender_neutral_caption>

    // <analyze>
    auto analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    auto result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        std::cout << " Image height = " << result->GetImageHeight().Value() << std::endl;
        std::cout << " Image width = " << result->GetImageWidth().Value() << std::endl;
        std::cout << " Model version = " << result->GetModelVersion().Value() << std::endl;

        const auto caption = result->GetCaption();
        if (caption.HasValue())
        {
            std::cout << " Caption:" << std::endl;
            std::cout << "   \"" << caption.Value().Content << "\", Confidence " << caption.Value().Confidence << std::endl;
        }

        const auto denseCaptions = result->GetDenseCaptions();
        if (denseCaptions.HasValue())
        {
            std::cout << " Dense Captions:" << std::endl;
            for (const auto caption : denseCaptions.Value())
            {
                std::cout << "   \"" << caption.Content << "\", ";
                std::cout << "Bounding box " << caption.BoundingBox.ToString();
                std::cout << ", Confidence " << caption.Confidence << std::endl;
            }
        }

        const auto objects = result->GetObjects();
        if (objects.HasValue())
        {
            std::cout << " Objects:" << std::endl;
            for (const auto object : objects.Value())
            {
                std::cout << "   \"" << object.Name << "\", ";
                std::cout << "Bounding box " << object.BoundingBox.ToString();
                std::cout << ", Confidence " << object.Confidence << std::endl;
            }
        }

        const auto tags = result->GetTags();
        if (tags.HasValue())
        {
            std::cout << " Tags:" << std::endl;
            for (const auto tag : tags.Value())
            {
                std::cout << "   \"" << tag.Name << "\"";
                std::cout << ", Confidence " << tag.Confidence << std::endl;
            }
        }

        const auto people = result->GetPeople();
        if (people.HasValue())
        {
            std::cout << " People:" << std::endl;
            for (const auto person : people.Value())
            {
                std::cout << "   Bounding box " << person.BoundingBox.ToString();
                std::cout << ", Confidence " << person.Confidence << std::endl;
            }
        }

        const auto cropSuggestions = result->GetCropSuggestions();
        if (cropSuggestions.HasValue())
        {
            std::cout << " Crop Suggestions:" << std::endl;
            for (const auto cropSuggestion : cropSuggestions.Value())
            {
                std::cout << "   Aspect ratio " << cropSuggestion.AspectRatio;
                std::cout << ": Crop suggestion " << cropSuggestion.BoundingBox.ToString() << std::endl;
            }
        }

        const auto detectedText = result->GetText();
        if (detectedText.HasValue())
        {
            std::cout << " Text:\n";
            for (const auto line : detectedText.Value().Lines)
            {
                std::cout << "   Line: \"" << line.Content << "\"";
                std::cout << ", Bounding polygon " << PolygonToString(line.BoundingPolygon) << std::endl;

                for (const auto word : line.Words)
                {
                    std::cout << "     Word: \"" << word.Content << "\"";
                    std::cout << ", Bounding polygon " << PolygonToString(word.BoundingPolygon);
                    std::cout << ", Confidence " << word.Confidence << std::endl;
                }
            }
        }

        auto resultDetails = ImageAnalysisResultDetails::FromResult(result);
        std::cout << " Result details:\n";;
        std::cout << "   Image ID = " << resultDetails->GetImageId() << std::endl;
        std::cout << "   Result ID = " << resultDetails->GetResultId() << std::endl;
        std::cout << "   Connection URL = " << resultDetails->GetConnectionUrl() << std::endl;
        std::cout << "   JSON result = " << resultDetails->GetJsonResult() << std::endl;
    }
    else
    {
        auto errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
    }
    // </analyze>
}

// <polygon_to_string>
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
// </polygon_to_string>

// <get_env_var>
std::string GetEnvironmentVariable(const std::string name)
{
#if defined(_MSC_VER)
    size_t size = 0;
    char buffer[1024];
    getenv_s(&size, nullptr, 0, name.c_str());
    if (size > 0 && size < sizeof(buffer))
    {
        getenv_s(&size, buffer, size, name.c_str());
        return std::string{ buffer };
    }
#else
    const char* value = getenv(name.c_str());
    if (value != nullptr)
    {
        return std::string{ value };
    }
#endif
    return std::string{""};
}
// </get_env_var>

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
// </snippet_single>
