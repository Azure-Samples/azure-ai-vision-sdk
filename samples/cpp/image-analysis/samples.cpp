//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Samples
//
#include <fstream>
#include <thread>
#include <vision_api_cxx_image_analyzer.hpp>

using namespace Azure::AI::Vision::Service;
using namespace Azure::AI::Vision::Input;
using namespace Azure::AI::Vision::ImageAnalysis;

// Forward declaration of helper functions
std::string PolygonToString(std::vector<int32_t> boundingPolygon);

// This sample does analysis on an image file using all visual features
// and a synchronous (blocking) call. It prints the results to the console,
// including the detailed results.
void ImageAnalysisSample_Analyze(std::string endpoint, std::string key)
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint(endpoint, key);

    // Specify the image file on disk to analyze. sample.jpg is a good example to show most features
    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("sample.jpg");

    // Or, instead of the above, specify a publicly accessible image URL to analyze
    // (e.g. https://aka.ms/azai/vision/image-analysis-sample.jpg)
    //std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("YourImageURL");

    // Creates the options object that will control the ImageAnalyzer
    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    // Mandatory. You must set one or more features to analyze. Here we use the full set of features.
    // Note that 'Caption' and 'DenseCaptions' are only supported in Azure GPU regions (East US, France Central, Korea Central,
    // North Europe, Southeast Asia, West Europe, West US). Remove 'Caption' and 'DenseCaptions' from the list below if your
    // Computer Vision key is not from one of those regions.
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

    // Optional, and only relevant when you select ImageAnalysisFeature::CropSuggestions.
    // Define one or more aspect ratios for the desired cropping. Each aspect ratio needs to be in the range [0.75, 1.8].
    // If you do not set this, the service will return one crop suggestion with the aspect ratio it sees fit.
    analysisOptions->SetCroppingAspectRatios({0.9, 1.33});

    // Optional. Default is "en" for English. See https://aka.ms/cv-languages for a list of supported
    // language codes and which visual features are supported for each language.
    analysisOptions->SetLanguage("en");

    // Optional. Default is "latest".
    analysisOptions->SetModelVersion("latest");

    // Optional, and only relevant when you select ImageAnalysisFeature::Caption.
    // Set this to "true" to get a gender neutral caption (the default is "false").
    analysisOptions->SetGenderNeutralCaption(true);

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::cout << " Please wait for image analysis results..." << std::endl << std::endl;

    // This call creates the network connection and blocks until Image Analysis results
    // return (or an error occurred). Note that there is also an asynchronous (non-blocking)
    // version of this method: analyzer->AnalyzeAsync().
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

        const Nullable<DenseCaptions>& denseCaptions = result->GetDenseCaptions();
        if (denseCaptions.HasValue())
        {
            std::cout << " Dense Captions:" << std::endl;
            for (const ContentCaption& caption : denseCaptions.Value())
            {
                std::cout << "   \"" << caption.Content << "\", ";
                std::cout << "Bounding box " << caption.BoundingBox.ToString();
                std::cout << ", Confidence " << caption.Confidence << std::endl;
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
                std::cout << ", Bounding polygon " << PolygonToString(line.BoundingPolygon) << std::endl;

                for (const DetectedTextWord& word: line.Words)
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
    else // result->GetReason() == ImageAnalysisResultReason::Error
    {
        std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
        std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
    }
}

// This sample does analysis on an image URL, using an asynchronous (non-blocking)
// call to analyze one visual feature (Tags)
void ImageAnalysisSample_AnalyzeAsync(std::string endpoint, std::string key)
{
    auto serviceOptions = VisionServiceOptions::FromEndpoint(endpoint, key);

    auto imageSource = VisionSource::FromUrl("https://aka.ms/azai/vision/image-analysis-sample.jpg");

    auto analysisOptions = ImageAnalysisOptions::Create();

    analysisOptions->SetFeatures( { ImageAnalysisFeature::Tags } );

    auto analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    bool eventRaised = false;

    analyzer->Analyzed.Connect([&eventRaised](const ImageAnalysisEventArgs& e)
    {
        auto result = e.GetResult();

        if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
        {
           auto tags = result->GetTags();
            if (tags.HasValue())
            {
                std::cout << " Tags:" << std::endl;
                for (auto tag : tags.Value())
                {
                    std::cout << "   \"" << tag.Name << "\"";
                    std::cout << ", Confidence " << tag.Confidence << std::endl;
                }
            }
        }
        else // result->GetReason() == ImageAnalysisResultReason::Error
        {
            auto errorDetails = ImageAnalysisErrorDetails::FromResult(result);
            std::cout << " Analysis failed." << std::endl;
            std::cout << "   Error reason =  " << (int)errorDetails->GetReason() << std::endl;
            std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
            std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
            std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
        }

        eventRaised = true;
    });

    std::cout << " Please wait for image analysis results..." << std::endl << std::endl;
    analyzer->AnalyzeAsync().get();

    while(!eventRaised)
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
}

// This sample does analysis on an image file using a given custom-trained model, and shows how
// to get the detected objects and/or tags
void ImageAnalysisSample_AnalyzeWithCustomModel(std::string endpoint, std::string key)
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint(endpoint, key);

    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("sample.jpg");

    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    // Set your custom model name here
    analysisOptions->SetModelName("MyCustomModelName");

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        // Use the GetCustomObjects() & GetCustomTags() methods to get the results

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
    else // result->GetReason() == ImageAnalysisResultReason::Error
    {
        std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
        std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
    }
}

// This sample does segmentation of an input image and writes the
// resulting background-removed image or foreground matte image to disk.
void ImageAnalysisSample_Segment(std::string endpoint, std::string key)
{
    std::shared_ptr<VisionServiceOptions> serviceOptions = VisionServiceOptions::FromEndpoint(endpoint, key);

    std::shared_ptr<VisionSource> imageSource = VisionSource::FromFile("sample.jpg");

    // Or, instead of the above, specify a publicly accessible image URL to analyze
    // (e.g. https://aka.ms/azai/vision/image-analysis-sample.jpg)
    //std::shared_ptr<VisionSource> imageSource = VisionSource::FromUrl("YourImageURL");

    std::shared_ptr<ImageAnalysisOptions> analysisOptions = ImageAnalysisOptions::Create();

    // Set one of two segmentation options: 'ImageSegmentationMode::BackgroundRemoval' or
    // 'ImageSegmentationMode::ForegroundMatting'
    analysisOptions->SetSegmentationMode(ImageSegmentationMode::BackgroundRemoval);

    std::shared_ptr<ImageAnalyzer> analyzer = ImageAnalyzer::Create(serviceOptions, imageSource, analysisOptions);

    std::cout << " Please wait for image analysis results..." << std::endl << std::endl;

    // This call creates the network connection and blocks until Image Analysis results
    // return (or an error occurred). Note that there is also an asynchronous (non-blocking)
    // version of this method: analyzer->AnalyzeAsync().
    std::shared_ptr<ImageAnalysisResult> result = analyzer->Analyze();

    if (result->GetReason() == ImageAnalysisResultReason::Analyzed)
    {
        SegmentationResult segmentationResult = result->GetSegmentationResult().Value();

        // Get the resulting output image buffer (PNG format)
        std::shared_ptr<uint8_t> imageBuffer = segmentationResult.GetImageBuffer();
        uint32_t imageBufferSize = segmentationResult.GetImageBufferSize();
        std::cout << " Segmentation result:" << std::endl;
        std::cout << "   Output image buffer size (bytes) = " << imageBufferSize << std::endl;

        // Get output image size
        std::cout << "   Output image height = " << segmentationResult.GetImageHeight() << std::endl;
        std::cout << "   Output image width = " << segmentationResult.GetImageWidth() << std::endl;

        // Write the buffer to a file
        std::string outputImageFile = "output.png";
        std::ofstream file(outputImageFile, std::ios::binary);
        file.write(reinterpret_cast<const char*>(imageBuffer.get()), imageBufferSize);
        std::cout << "   File " << outputImageFile << " written to disk" << std::endl;
    }
    else // result->GetReason() == ImageAnalysisResultReason::Error
    {
        std::shared_ptr<ImageAnalysisErrorDetails> errorDetails = ImageAnalysisErrorDetails::FromResult(result);
        std::cout << " Analysis failed." << std::endl;
        std::cout << "   Error reason = " << (int)errorDetails->GetReason() << std::endl;
        std::cout << "   Error code = " << errorDetails->GetErrorCode() << std::endl;
        std::cout << "   Error message = " << errorDetails->GetMessage() << std::endl;
        std::cout << " Did you set the computer vision endpoint and key?" << std::endl;
    }
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


