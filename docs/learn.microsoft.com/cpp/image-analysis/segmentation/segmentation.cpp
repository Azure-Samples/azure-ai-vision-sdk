// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/background-removal?tabs=cpp

#include <fstream>
#include <vision_api_cxx_image_analyzer.hpp>

using namespace Azure::AI::Vision::ImageAnalysis;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::Service;

std::string GetEnvironmentVariable(const std::string name);

void SegmentImage()
{
    auto serviceOptions = VisionServiceOptions::FromEndpoint(
        GetEnvironmentVariable("VISION_ENDPOINT"),
        GetEnvironmentVariable("VISION_KEY"));

    auto imageSource = VisionSource::FromUrl(
        "https://learn.microsoft.com/azure/ai-services/computer-vision/media/quickstarts/presentation.png");

    // <segmentation_mode>
    auto analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetSegmentationMode(ImageSegmentationMode::BackgroundRemoval);
    // </segmentation_mode>

    // <segment>
    auto analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    auto result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        auto segmentationResult = result->GetSegmentationResult().Value();

        auto imageBuffer = segmentationResult.GetImageBuffer();
        auto imageBufferSize = segmentationResult.GetImageBufferSize();
        std::cout << " Segmentation result:" << std::endl;
        std::cout << "   Output image buffer size (bytes) = " << imageBufferSize << std::endl;
        std::cout << "   Output image height = " << segmentationResult.GetImageHeight() << std::endl;
        std::cout << "   Output image width = " << segmentationResult.GetImageWidth() << std::endl;

        std::string outputImageFile = "output.png";
        std::ofstream file(outputImageFile, std::ios::binary);
        file.write(reinterpret_cast<const char*>(imageBuffer.get()), imageBufferSize);
        std::cout << "   File " << outputImageFile << " written to disk" << std::endl;
    }
    else
    {
        auto errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
    }
    // </segment>
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
        SegmentImage();
    }
    catch (std::exception e)
    {
        std::cout << e.what();
    }

    return 0;
}

