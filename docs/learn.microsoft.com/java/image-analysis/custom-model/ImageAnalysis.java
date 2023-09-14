// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-java#get-results-using-custom-model

import java.net.URL;

import com.azure.ai.vision.common.*;
import com.azure.ai.vision.imageanalysis.*;

public class ImageAnalysis {

    public static void main(String[] args) {
        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(
                new URL(System.getenv("VISION_ENDPOINT_CUSTOM_MODEL")),
                System.getenv("VISION_KEY_CUSTOM_MODEL"));

            VisionSource imageSource = VisionSource.fromUrl(
                new URL("https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png"))) {

            // <model_name>
            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions();

            analysisOptions.setModelName("YourCustomModelNameHere");
            // </model_name>

            // <analyze>
            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);
                ImageAnalysisResult result = analyzer.analyze()) {

                if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {

                    if (result.getCustomObjects() != null) {
                        System.out.println("Custom Objects:");
                        for (DetectedObject detectedObject : result.getCustomObjects()) {
                            System.out.println(String.format("\"%s\", Bounding box %s, Confidence %.4f",
                                    detectedObject.getName(), detectedObject.getBoundingBox(),
                                    detectedObject.getConfidence()));
                        }
                    }

                    if (result.getCustomTags() != null) {
                        System.out.println("Custom Tags:");
                        for (ContentTag tag : result.getCustomTags()) {
                            System.out.println(String.format("   \"%s\", Confidence %.4f",
                                    tag.getName(), tag.getConfidence()));
                        }
                    }

                } else { // result.getReason() == ImageAnalysisResultReason.ERROR
                    ImageAnalysisErrorDetails errorDetails = ImageAnalysisErrorDetails.fromResult(result);
                    System.out.println("Analysis failed.");
                    System.out.println("Error reason: " + errorDetails.getReason());
                    System.out.println("Error code: " + errorDetails.getErrorCode());
                    System.out.println("Error message: " + errorDetails.getMessage());
                    System.out.println("Did you set the computer vision endpoint and key?");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            // </analyze>

            analysisOptions.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
