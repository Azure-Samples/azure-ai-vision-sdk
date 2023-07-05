// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/cognitive-services/computer-vision/how-to/call-analyze-image-40?tabs=csharp

// <snippet_single>
using Azure;
using Azure.AI.Vision.Common.Input;
using Azure.AI.Vision.Common.Options;
using Azure.AI.Vision.ImageAnalysis;

class Program
{
    static void SegmentImage()
    {
        // <vision_service_options>
        var serviceOptions = new VisionServiceOptions(
            Environment.GetEnvironmentVariable("VISION_ENDPOINT"),
            new AzureKeyCredential(Environment.GetEnvironmentVariable("VISION_KEY")));
        // </vision_service_options>

        // <vision_source>
        using var imageSource = VisionSource.FromUrl(
            new Uri("https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png"));
        // </vision_source>

        // <segmentation_mode>
        var analysisOptions = new ImageAnalysisOptions()
        {
            SegmentationMode = ImageSegmentationMode.BackgroundRemoval
        };
        // </segmentation_mode>

        // <segment>
        using var analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

        var result = analyzer.Analyze();

        if (result.Reason == ImageAnalysisResultReason.Analyzed)
        {
            using var segmentationResult = result.SegmentationResult;

            var imageBuffer = segmentationResult.ImageBuffer;
            Console.WriteLine($" Segmentation result:");
            Console.WriteLine($"   Output image buffer size (bytes) = {imageBuffer.Length}");
            Console.WriteLine($"   Output image height = {segmentationResult.ImageHeight}");
            Console.WriteLine($"   Output image width = {segmentationResult.ImageWidth}");

            string outputImageFile = "output.png";
            using (var fs = new FileStream(outputImageFile, FileMode.Create))
            {
                fs.Write(imageBuffer.Span);
            }
            Console.WriteLine($"   File {outputImageFile} written to disk");
        }
        else
        {
            var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
            Console.WriteLine(" Analysis failed.");
            Console.WriteLine($"   Error reason : {errorDetails.Reason}");
            Console.WriteLine($"   Error code : {errorDetails.ErrorCode}");
            Console.WriteLine($"   Error message: {errorDetails.Message}");
            Console.WriteLine(" Did you set the computer vision endpoint and key?");
        }
        // </segment>
    }

    static void Main()
    {
        try
        {
            SegmentImage();
        }
        catch (Exception e)
        {
            Console.WriteLine(e);
        }
    }
}
// </snippet_single>
