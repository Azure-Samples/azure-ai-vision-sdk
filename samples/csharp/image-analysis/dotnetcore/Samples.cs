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
        public static void GetAllResults(string endpoint, string key)
        {
            var serviceOptions = new VisionServiceOptions(endpoint, key);

            // Specify the image file on disk to analyze. sample1.jpg is a good example to show most features,
            // except Text (OCR). Use sample2.jpg for OCR.
            using var imageSource = VisionSource.FromFile("sample1.jpg");

            // Or, instead of the above, specify a publicly accessible image URL to analyze
            // (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
            // var imageSource = VisionSource.FromUrl(new Uri("YourImageURL"));

            var analysisOptions = new ImageAnalysisOptions()
            {
                // Mandatory. You must set one or more features to analyze. Here we use the full set of features.
                // Note that 'Captions' is only supported in Azure GPU regions (East US, France Central, Korea Central,
                // North Europe, Southeast Asia, West Europe, West US)
                Features =
                      ImageAnalysisFeature.CropSuggestions
                    | ImageAnalysisFeature.Captions
                    | ImageAnalysisFeature.Objects
                    | ImageAnalysisFeature.People
                    | ImageAnalysisFeature.Text
                    | ImageAnalysisFeature.Tags,

                // Optional, and only relevant when you select ImageAnalysisFeature.CropSuggestions.
                // Define one or more aspect ratios for the desired cropping. Each aspect ratio needs to be in the range [0.75, 1.8].
                // If you do not set this, the service will return one crop suggestion with the aspect ratio it sees fit.
                CroppingAspectRatios = new List<double>() { 0.9, 1.33 },

                // Optional. Default is "en" for English. See https://aka.ms/cv-languages for a list of supported
                // language codes and which visual features are supported for each language.
                Language = "en",

                // Optional. Default is "latest".
                ModelVersion = "latest",

                // Optional, and only relevant when you select ImageAnalysisFeature::Captions.
                // Set this to "true" to get gender neutral captions (the default is "false").
                GenderNeutralCaptions = true
            };

            using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

            Console.WriteLine(" Please wait for image analysis results...\n");

            // This call creates the network connection and blocks until Image Analysis results
            // return (or an error occurred). Note that there is also an asynchronous (non-blocking)
            // version of this method: analyzer.AnalyzeAsync().
            var result = analyzer.Analyze();

            if (result.Reason == ImageAnalysisResultReason.Analyzed)
            {
                Console.WriteLine($" Image height = {result.ImageHeight}");
                Console.WriteLine($" Image width = {result.ImageWidth}");
                Console.WriteLine($" Model version = {result.ModelVersion}");

                if (result.Captions != null)
                {
                    Console.WriteLine(" Captions:");
                    foreach (var caption in result.Captions)
                    {
                        Console.WriteLine($"   \"{caption.Content}\", Confidence {caption.Confidence:0.0000}");
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
                var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
                Console.WriteLine(" Analysis failed.");
                Console.WriteLine($"   Error reason : {errorDetails.Reason}");
                Console.WriteLine($"   Error code : {errorDetails.ErrorCode}");
                Console.WriteLine($"   Error message: {errorDetails.Message}");
                Console.WriteLine(" Did you set the computer vision endpoint and key?");
            }
        }

        // This sample does analysis on an image URL, showing how to use the Analyzed event to get
        // the analysis result for one visual feature (tags).
        public static async Task GetResultsUsingAnalyzedEvent(string endpoint, string key)
        {
            var serviceOptions = new VisionServiceOptions(endpoint, key);

            using var imageSource = VisionSource.FromUrl(new Uri("https://docs.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg"));

            var analysisOptions = new ImageAnalysisOptions() { Features = ImageAnalysisFeature.Tags };

            using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

            var tcsEventReceived  = new TaskCompletionSource<bool>();

            analyzer.Analyzed += (_, e) =>
            {
                if (e.Result.Reason == ImageAnalysisResultReason.Analyzed)
                {
                    Console.WriteLine($" Tags:");
                    foreach (var tag in e.Result.Tags)
                    {
                        Console.WriteLine($"   \"{tag.Name}\", Confidence {tag.Confidence:0.0000}");
                    }
                }
                else if (e.Result.Reason == ImageAnalysisResultReason.Error)
                {
                    Console.WriteLine(" Analysis failed.");

                    var errorDetails = ImageAnalysisErrorDetails.FromResult(e.Result);
                    Console.WriteLine($"   Error reason : {errorDetails.Reason}");
                    Console.WriteLine($"   Error code : {errorDetails.ErrorCode}");
                    Console.WriteLine($"   Error message: {errorDetails.Message}");
                    Console.WriteLine(" Did you set the computer vision endpoint and key?");
                }

                tcsEventReceived.SetResult(true);
            };

            Console.WriteLine(" Please wait for image analysis results...");
            await analyzer.AnalyzeAsync();

            // Make sure we received the Analyzed event before exiting this method
            Task.WaitAny(tcsEventReceived.Task);
        }
    }
}
