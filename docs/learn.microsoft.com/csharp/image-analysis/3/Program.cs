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
        var serviceOptions = new VisionServiceOptions("PASTE_YOUR_COMPUTER_VISION_ENDPOINT_HERE", "PASTE_YOUR_COMPUTER_VISION_SUBSCRIPTION_KEY_HERE");

        var imageSource = VisionSource.FromUrl(new Uri("https://csspeechstorage.blob.core.windows.net/drop/TestData/images/ocr-sample.jpg"));

        var analysisOptions = new ImageAnalysisOptions()
        {
            // Set your custom model name here
            ModelName = "MyCustomModelName"
        };

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