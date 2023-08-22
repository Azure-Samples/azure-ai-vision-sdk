// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-cpp#custom-model

#include <vision_api_cxx_image_analyzer.hpp>

using namespace Azure::AI::Vision::ImageAnalysis;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::Service;

std::string GetEnvironmentVariable(const std::string name);

void AnalyzeImage()
{
    auto serviceOptions = VisionServiceOptions::FromEndpoint(
        GetEnvironmentVariable("VISION_ENDPOINT"),
        GetEnvironmentVariable("VISION_KEY"));

    auto imageSource = VisionSource::FromUrl(
        "https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png");

    // <model_name>
    auto analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetModelName("MyCustomModelName");
    // </model_name>

    // <analyze>
    auto analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    auto result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        const auto objects = result->GetCustomObjects();
        if (objects.HasValue())
        {
            std::cout << " Custom objects:" << std::endl;
            for (const auto object : objects.Value())
            {
                std::cout << "   \"" << object.Name << "\", ";
                std::cout << "Bounding box " << object.BoundingBox.ToString();
                std::cout << ", Confidence " << object.Confidence << std::endl;
            }
        }

        const auto tags = result->GetCustomTags();
        if (tags.HasValue())
        {
            std::cout << " Custom tags:" << std::endl;
            for (const auto tag : tags.Value())
            {
                std::cout << "   \"" << tag.Name << "\"";
                std::cout << ", Confidence " << tag.Confidence << std::endl;
            }
        }
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
    return std::string{ "" };
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

