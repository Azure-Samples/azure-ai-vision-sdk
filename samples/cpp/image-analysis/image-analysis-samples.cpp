//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Samples
//
#include <memory>
#include <iostream>
#include <vision_api_cxx.h>

using namespace Azure::AI::Vision::Service;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::ImageAnalysis;

// Forward declaration of helper functions
std::string PolygonToString(std::vector<int32_t> boundingPolygon);

// This sample does analysis on an image file using all visual features, and prints the results to the console,
// including the detailed results.
void ImagAnalysisSample_GetAllResults(std::string endpoint, std::string key)
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint(endpoint, key);

    // Specify the image file on disk to analyze
    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("laptop-on-kitchen-table.jpg");

    // Or, instead of the above, specify a publicly accessible image URL to analyze
    // (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
    //std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("YourImageURL");

    // Creates the options object that will control the ImageAnalyzer
    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    // Mandatory. You must set one or more features to analyze. Here we use the full set of features.
    analysisOptions->SetFeatures(
    {
        ImageAnalysisFeature::CropSuggestions,
        ImageAnalysisFeature::Descriptions,
        ImageAnalysisFeature::Objects,
        ImageAnalysisFeature::People,
        ImageAnalysisFeature::Text,
        ImageAnalysisFeature::Tags
    });

    // Optional, and only relevant when you select ImageAnalysisFeature::CropSuggestions.
    // Default ratio is 1.94. Each aspect ratio needs to be in the range [0.75, 1.8].
    analysisOptions->SetCroppingAspectRatios({1.0, 1.33});

    // Optional. Default is "en" for English.
    analysisOptions->SetLanguage("en");

    // Optional. Default is "latest".
    analysisOptions->SetModelVersion("latest");

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::cout << " Please wait for image analysis results...\n\n";
    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        std::cout << " Image height = " << result->GetImageHeight().Value() << std::endl;
        std::cout << " Image width = " << result->GetImageWidth().Value() << std::endl;
        std::cout << " Model version = " << result->GetModelVersion().Value() << std::endl;

        const Nullable<ContentDescriptions>& descriptions = result->GetDescriptions();
        if (descriptions.HasValue())
        {
            std::cout << " Descriptions:" << std::endl;
            for (const ContentDescription& description : descriptions.Value())
            {
                std::cout << "   \"" << description.Content;
                std::cout << "\", Confidence " << description.Confidence << std::endl;
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

        const Nullable<ContentTags>& tags = result->GetTags();
        if (tags.HasValue())
        {
            std::cout << " Tags:" << std::endl;
            for (const ContentTag& tag : tags.Value())
            {
                std::cout << "   \"" << tag.Name << "\"";
                std::cout << ", Confidence " << tag.Confidence << std::endl;
            }
        }

        const Nullable<DetectedPeople>& people = result->GetPeople();
        if (people.HasValue())
        {
            std::cout << " People:" << std::endl;
            for (const DetectedPerson& person : people.Value())
            {
                std::cout << "   Bounding box " << person.BoundingBox.ToString();
                std::cout << ", Confidence " << person.Confidence << std::endl;
            }
        }

        const Nullable<CropSuggestions>& cropSuggestions = result->GetCropSuggestions();
        if (cropSuggestions.HasValue())
        {
            std::cout << " Crop Suggestions:" << std::endl;
            for (const CropSuggestion& cropSuggestion : cropSuggestions.Value())
            {
                std::cout << "   Aspect ratio " << cropSuggestion.AspectRatio; 
                std::cout << ": Crop suggestion " << cropSuggestion.BoundingBox.ToString() << std::endl;
            }
        }

        const Nullable<DetectedText>& detectedText = result->GetText();
        if (detectedText.HasValue())
        {
            std::cout << " Text:\n";
            for (const DetectedTextLine& line : detectedText.Value().Lines)
            {
                std::cout << "   Line: \"" << line.Content << "\"";
                std::cout << ", Bounding polygon " << PolygonToString(line.BoundingPolygon) << "}\n";

                for (const DetectedTextWord& word: line.Words)
                {
                    std::cout << "     Word: \"" << word.Content << "\"";
                    std::cout << ", Bounding polygon " << PolygonToString(word.BoundingPolygon);
                    std::cout << ", Confidence " << word.Confidence << std::endl;
                }
            }
        }

        std::cout << " Detailed result:\n";;
        std::cout << "   Image ID = " << result->GetImageId() << std::endl;
        std::cout << "   Result ID = " << result->GetResultId() << std::endl;
        std::cout << "   JSON = " << result->GetJsonResult() << std::endl;
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

void ImagAnalysisSample_GetResultsUsingAnalyzedEvent(std::string /*endpoint*/, std::string /*key*/)
{
    // TODO
}

void ImagAnalysisSample_UsingFrameSource(std::string /*endpoint*/, std::string /*key*/)
{
    // TODO
}

// Helper method to display the values of a bounding polygon
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


