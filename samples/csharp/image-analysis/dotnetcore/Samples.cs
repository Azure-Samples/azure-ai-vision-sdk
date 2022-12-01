//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C# Image Analysis Samples
//
using Azure.AI.Vision.Core.Input;
using Azure.AI.Vision.Core.Options;
using Azure.AI.Vision.ImageAnalysis;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace ImageAnalysisSamples
{
    public class Samples
    {
        // This sample does analysis on an image file using all visual features, and prints the results to the console,
        // including the detailed results.
        public static async Task GetAllResults(string endpoint, string key)
        {
            var serviceOptions = new VisionServiceOptions(endpoint, key);

            // Specify an image file on disk to analyze
            var imageSource = VisionSource.FromFile("laptop-on-kitchen-table.jpg");

            // Or, instead of the above, specify a publicly accessible image URL to analyze
            // (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
            // var imageSource = VisionSource.FromUrl(new Uri("YourImageURL"));

            var analysisOptions = new ImageAnalysisOptions()
            {
                // Mandatory. You must set one or more features to analyze. Here we use the full set of features.
                Features =
                      ImageAnalysisFeature.CropSuggestions
                    | ImageAnalysisFeature.Descriptions
                    | ImageAnalysisFeature.Objects
                    | ImageAnalysisFeature.People
                    | ImageAnalysisFeature.Text
                    | ImageAnalysisFeature.Tags,

                // Optional, and only relevant when you select ImageAnalysisFeature.CropSuggestions.
                // Default ratio is 1.94. Each aspect ratio needs to be in the range [0.75, 1.8].
                CroppingAspectRatios = new List<double>()
                {
                    1.0,
                    1.33,
                },

                // Optional. Default is "en" for English.
                Language = "en",

                // Optional. Default is "latest".
                ModelVersion = "latest"
            };

            using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

            Console.WriteLine(" Please wait for image analysis results...\n");
            var result = await analyzer.AnalyzeAsync();

            if (result.Reason == ImageAnalysisResultReason.Analyzed)
            {
                Console.WriteLine($" Image height = {result.ImageHeight}");
                Console.WriteLine($" Image width = {result.ImageWidth}");
                Console.WriteLine($" Model version = {result.ModelVersion}");

                if (result.Descriptions != null)
                {
                    Console.WriteLine(" Descriptions:");
                    foreach (var description in result.Descriptions)
                    {
                        Console.WriteLine($"   \"{description.Content}\", Confidence {description.Confidence:0.0000}");
                    };
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

                var detailedResult = ImageAnalysisResultDetails.FromResult(result);
                Console.WriteLine($" Detailed result:");
                Console.WriteLine($"   Image ID = {detailedResult.ImageId}");
                Console.WriteLine($"   Result ID = {detailedResult.ResultId}");
                Console.WriteLine($"   JSON = {detailedResult.JsonResult}");
            }
            else if (result.Reason == ImageAnalysisResultReason.Error)
            {
                Console.WriteLine(" Analysis failed.");

                var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
                Console.WriteLine($"   Error reason : {errorDetails.Reason}");
                Console.WriteLine($"   Error message: {errorDetails.Message}");
                Console.WriteLine(" Did you set the computer vision endpoint and key?");
            }
        }

        // This sample does analysis on an image URL, showing how to use the Analyzed event to get
        // the analysis result for one visual feature
        public static async Task GetResultsUsingAnalyzedEvent(string endpoint, string key)
        {
            var serviceOptions = new VisionServiceOptions(endpoint, key);

            var imageSource = VisionSource.FromUrl(new Uri("https://docs.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg"));

            var analysisOptions = new ImageAnalysisOptions() { Features = ImageAnalysisFeature.Descriptions };

            using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

            analyzer.Analyzed += (_, e) =>
            {
                if (e.Result.Reason == ImageAnalysisResultReason.Analyzed)
                {
                    Console.WriteLine(" Descriptions:");
                    foreach (var description in e.Result.Descriptions)
                    {
                        Console.WriteLine($"  \"{description.Content}\"");
                    };
                }
                else if (e.Result.Reason == ImageAnalysisResultReason.Error)
                {
                    Console.WriteLine(" Analysis failed.");

                    var errorDetails = ImageAnalysisErrorDetails.FromResult(e.Result);
                    Console.WriteLine($"   Error reason : {errorDetails.Reason}");
                    Console.WriteLine($"   Error message: {errorDetails.Message}");
                    Console.WriteLine(" Did you set the computer vision endpoint and key?");
                }
            };

            Console.WriteLine(" Please wait for image analysis results...");
            _ = await analyzer.AnalyzeAsync();

            Thread.Sleep(3000);
        }

        // This sample does analysis on an image that is provided as a memory buffer
        public static void /*async Task*/ UsingFrameSource(string endpoint, string key)
        {
            // TODO
        }
    }
}
