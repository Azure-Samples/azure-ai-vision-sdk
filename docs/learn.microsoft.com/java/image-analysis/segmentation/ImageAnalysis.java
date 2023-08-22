// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/background-removal?tabs=java


// <snippet_single>
import java.net.URL;
import java.util.EnumSet;
import java.io.FileOutputStream;

import com.azure.ai.vision.common.*;
import com.azure.ai.vision.imageanalysis.*;

public class ImageAnalysis {

    public static void main(String[] args) {

        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(
                new URL(System.getenv("VISION_ENDPOINT")),
                System.getenv("VISION_KEY"));

            VisionSource imageSource = VisionSource.fromUrl(
                new URL("https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png"));

            // <segmentation_mode>
            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions();
            // <ignore>
            ) {
            // </ignore>

            analysisOptions.setSegmentationMode(ImageSegmentationMode.BACKGROUND_REMOVAL);
            // </segmentation_mode>

            // <segment>
            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

                ImageAnalysisResult result = analyzer.analyze()) {

                if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {

                    SegmentationResult segmentationResult = result.getSegmentationResult();

                    // Get the resulting output image buffer (PNG format)
                    byte[] imageBuffer = segmentationResult.getImageBuffer();
                    System.out.println(" Segmentation result:");
                    System.out.println("   Output image buffer size (bytes) = " + imageBuffer.length);

                    // Get output image size
                    System.out.println("   Output image height = " + segmentationResult.getImageHeight());
                    System.out.println("   Output image width = " + segmentationResult.getImageWidth());

                    // Write the buffer to a file
                    String outputImageFile = "output.png";
                    try (FileOutputStream fos = new FileOutputStream(outputImageFile)) {
                        fos.write(imageBuffer);
                    }
                    System.out.println("   File " + outputImageFile + " written to disk");

                } else { // result.getReason() == ImageAnalysisResultReason.ERROR
                    ImageAnalysisErrorDetails errorDetails = ImageAnalysisErrorDetails.fromResult(result);
                    System.out.println(" Analysis failed.");
                    System.out.println("   Error reason: " + errorDetails.getReason());
                    System.out.println("   Error code: " + errorDetails.getErrorCode());
                    System.out.println("   Error message: " + errorDetails.getMessage());
                    System.out.println(" Did you set the computer vision endpoint and key?");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } 
            // <segment>

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
// </snippet_single>