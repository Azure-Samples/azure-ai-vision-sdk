//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C# Image Analysis Quickstart
//
// <doc-tag-csharp-image-analysis-quickstart>
using Azure.AI.Vision.Core.Input;
using Azure.AI.Vision.Core.Options;
using Azure.AI.Vision.ImageAnalysis;
using System;
using System.Threading.Tasks;

class Program
{
    public static async Task Main()
    {
        // Replace the string "YourComputerVisionEndpoint" with your Computer Vision endpoint, found in the Azure portal.
        // The endpoint has the form "https://<your-computer-vision-resource-name>.cognitiveservices.azure.com".
        // Replace the string "YourComputerVisionKey" with your Computer Vision key. The key is a 32-character
        // HEX number (no dashes), found in the Azure portal. Similar to "d0dbd4c2a93346f18c785a426da83e15".
        var serviceOptions = new VisionServiceOptions("YourComputerVisionEndpoint", "YourComputerVisionKey");

        // Specify the image file on disk to analyze
        var imageSource = VisionSource.FromFile("laptop-on-kitchen-table.jpg");

        // Or, instead of the above, specify a publicly accessible image URL to analyze
        // (e.g. https://learn.microsoft.com/azure/cognitive-services/computer-vision/images/windows-kitchen.jpg)
        // var imageSource = VisionSource.FromUrl(new Uri("YourImageURL"));

        // Specify the options that will control the ImageAnalyzer
        var analysisOptions = new ImageAnalysisOptions()
        {
            // You must define one or more features to extract during image analysis
            Features = ImageAnalysisFeature.Descriptions | ImageAnalysisFeature.Objects
        };

        using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

        Console.WriteLine(" Please wait for image analysis results...");
        var result = await analyzer.AnalyzeAsync();

        if (result.Reason == ImageAnalysisResultReason.Analyzed)
        {
            if (result.Descriptions != null)
            {
                Console.WriteLine($" Descriptions:");
                foreach (var description in result.Descriptions)
                {
                    Console.WriteLine($"   \"{description.Content}\", Confidence {description.Confidence:0.0000}");
                };
            }

            if (result.Objects != null)
            {
                Console.WriteLine($" Objects:");
                foreach (var detectedObject in result.Objects)
                {
                    Console.WriteLine($"   \"{detectedObject.Name}\", Bounding box {detectedObject.BoundingBox}, Confidence {detectedObject.Confidence:0.0000}");
                }
            }
        }
        else if (result.Reason == ImageAnalysisResultReason.Error)
        {
            Console.WriteLine($" Analysis failed.");

            var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
            Console.WriteLine($"   Error reason : {errorDetails.Reason}");
            Console.WriteLine($"   Error message: {errorDetails.Message}");
            Console.WriteLine($" Did you set the computer vision endpoint and key?");
        }
    }
}
// </doc-tag-csharp-image-analysis-quickstart>
