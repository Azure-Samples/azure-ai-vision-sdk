//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

// <snippet-single>
using Azure.AI.Vision.Core.Input;
using Azure.AI.Vision.Core.Options;
using Azure.AI.Vision.ImageAnalysis;

class Program
{
    static void AnalyzeImage()
    {
        var serviceOptions = new VisionServiceOptions(
            Environment.GetEnvironmentVariable("VISION_ENDPOINT"),
            Environment.GetEnvironmentVariable("VISION_KEY"));

        var imageSource = VisionSource.FromUrl(new Uri("https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg"));

        var analysisOptions = new ImageAnalysisOptions()
        {
            Features =
                  ImageAnalysisFeature.CropSuggestions
                | ImageAnalysisFeature.Caption
                | ImageAnalysisFeature.Objects
                | ImageAnalysisFeature.People
                | ImageAnalysisFeature.Text
                | ImageAnalysisFeature.Tags,

            CroppingAspectRatios = new List<double>() { 0.9, 1.33 },

            Language = "en",

            GenderNeutralCaption = true
        };

        using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

        var result = analyzer.Analyze();

        if (result.Reason == ImageAnalysisResultReason.Analyzed)
        {
            Console.WriteLine($" Image height = {result.ImageHeight}");
            Console.WriteLine($" Image width = {result.ImageWidth}");
            Console.WriteLine($" Model version = {result.ModelVersion}");

            if (result.Caption != null)
            {
                Console.WriteLine(" Caption:");
                Console.WriteLine($"   \"{result.Caption.Content}\", Confidence {result.Caption.Confidence:0.0000}");
            }

            if (result.Objects != null)
            {
                Console.WriteLine(" Objects:");
                foreach (var detectedObject in result.Objects)
                {
                    Console.WriteLine($"   \"{detectedObject.Name}\", Bounding box {detectedObject.BoundingBox}, Confidence {detectedObject.Confidence:0.0000}");
                }
            }

            if (result.Tags != null)
            {
                Console.WriteLine($" Tags:");
                foreach (var tag in result.Tags)
                {
                    Console.WriteLine($"   \"{tag.Name}\", Confidence {tag.Confidence:0.0000}");
                }
            }

            if (result.People != null)
            {
                Console.WriteLine($" People:");
                foreach (var person in result.People)
                {
                    Console.WriteLine($"   Bounding box {person.BoundingBox}, Confidence {person.Confidence:0.0000}");
                }
            }

            if (result.CropSuggestions != null)
            {
                Console.WriteLine($" Crop Suggestions:");
                foreach (var cropSuggestion in result.CropSuggestions)
                {
                    Console.WriteLine($"   Aspect ratio {cropSuggestion.AspectRatio}: "
                        + $"Crop suggestion {cropSuggestion.BoundingBox}");
                };
            }

            if (result.Text != null)
            {
                Console.WriteLine($" Text:");
                foreach (var line in result.Text.Lines)
                {
                    string pointsToString = "{" + string.Join(',', line.BoundingPolygon.Select(pointsToString => pointsToString.ToString())) + "}";
                    Console.WriteLine($"   Line: '{line.Content}', Bounding polygon {pointsToString}");

                    foreach (var word in line.Words)
                    {
                        pointsToString = "{" + string.Join(',', word.BoundingPolygon.Select(pointsToString => pointsToString.ToString())) + "}";
                        Console.WriteLine($"     Word: '{word.Content}', Bounding polygon {pointsToString}, Confidence {word.Confidence:0.0000}");
                    }
                }
            }

            var resultDetails = ImageAnalysisResultDetails.FromResult(result);
            Console.WriteLine($" Result details:");
            Console.WriteLine($"   Image ID = {resultDetails.ImageId}");
            Console.WriteLine($"   Result ID = {resultDetails.ResultId}");
            Console.WriteLine($"   Connection URL = {resultDetails.ConnectionUrl}");
            Console.WriteLine($"   JSON result = {resultDetails.JsonResult}");
        }
        else if (result.Reason == ImageAnalysisResultReason.Error)
        {
            var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
            Console.WriteLine(" Analysis failed.");
            Console.WriteLine($"   Error reason : {errorDetails.Reason}");
            Console.WriteLine($"   Error code : {errorDetails.ErrorCode}");
            Console.WriteLine($"   Error message: {errorDetails.Message}");
        }
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
// </snippet-single>