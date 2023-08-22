// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-csharp#get-results-using-custom-model

using Azure;
using Azure.AI.Vision.Common;
using Azure.AI.Vision.ImageAnalysis;

class Program
{
    static void AnalyzeImage()
    {
        var serviceOptions = new VisionServiceOptions(
            Environment.GetEnvironmentVariable("VISION_ENDPOINT"),
            new AzureKeyCredential(Environment.GetEnvironmentVariable("VISION_KEY")));

        using var imageSource = VisionSource.FromUrl(
            new Uri("https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png"));

        // <model_name>
        var analysisOptions = new ImageAnalysisOptions()
        {
            ModelName = "MyCustomModelName"
        };
        // </model_name>

        // <analyze>
        using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

        var result = analyzer.Analyze();

        if (result.Reason == ImageAnalysisResultReason.Analyzed)
        {
            if (result.CustomObjects != null)
            {
                Console.WriteLine(" Custom Objects:");
                foreach (var detectedObject in result.CustomObjects)
                {
                    Console.WriteLine($"   \"{detectedObject.Name}\", Bounding box {detectedObject.BoundingBox}, Confidence {detectedObject.Confidence:0.0000}");
                }
            }

            if (result.CustomTags != null)
            {
                Console.WriteLine($" Custom Tags:");
                foreach (var tag in result.CustomTags)
                {
                    Console.WriteLine($"   \"{tag.Name}\", Confidence {tag.Confidence:0.0000}");
                }
            }
        }
        else
        {
            var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
            Console.WriteLine(" Analysis failed.");
            Console.WriteLine($"   Error reason : {errorDetails.Reason}");
            Console.WriteLine($"   Error code : {errorDetails.ErrorCode}");
            Console.WriteLine($"   Error message: {errorDetails.Message}");
        }
        // </analyze>
    }

    static void Main()
    {
        try
        {
            AnalyzeImage();
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
    }
}
