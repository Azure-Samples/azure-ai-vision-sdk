// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/quickstarts-sdk/image-analysis-client-library-40?tabs=visual-studio%2Cwindows&pivots=programming-language-java#analyze-image


// <snippet_single>
import java.net.URL;
import java.util.EnumSet;

import com.azure.ai.vision.common.*;
import com.azure.ai.vision.imageanalysis.*;

public class ImageAnalysis {

    public static void main(String[] args) {

        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(
                new URL(System.getenv("VISION_ENDPOINT")),
                System.getenv("VISION_KEY"));

            VisionSource imageSource = VisionSource.fromUrl(
                new URL("https://learn.microsoft.com/azure/ai-services/computer-vision/media/quickstarts/presentation.png"));

            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions()) {

            analysisOptions.setFeatures(EnumSet.of(ImageAnalysisFeature.CAPTION, ImageAnalysisFeature.TEXT));

            analysisOptions.setLanguage("en");

            analysisOptions.setGenderNeutralCaption(true);

            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);

                ImageAnalysisResult result = analyzer.analyze()) {

                if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {

                    if (result.getCaption() != null) {
                        System.out.println(" Caption:");
                        System.out.println("   \"" + result.getCaption().getContent() + "\", Confidence "
                                + String.format("%.4f", result.getCaption().getConfidence()));
                    }

                    if (result.getText() != null) {
                        System.out.println(" Text:");
                        for (DetectedTextLine line : result.getText()) {
                            String pointsToString = "{" + String.join(",",
                                    line.getBoundingPolygon().stream().map(Object::toString).toArray(String[]::new))
                                    + "}";
                            System.out.println(
                                    "   Line: '" + line.getContent() + "', Bounding polygon " + pointsToString);

                            for (DetectedTextWord word : line.getWords()) {
                                pointsToString = "{" + String.join(",",
                                        word.getBoundingPolygon().stream().map(Object::toString).toArray(String[]::new))
                                        + "}";
                                System.out.println(
                                        "     Word: '" + word.getContent() + "', Bounding polygon " + pointsToString +
                                                ", Confidence " + String.format("%.4f", word.getConfidence()));
                            }
                        }
                    }
                } else { // result.Reason == ImageAnalysisResultReason.Error
                    ImageAnalysisErrorDetails errorDetails = ImageAnalysisErrorDetails.fromResult(result);
                    System.out.println(" Analysis failed.");
                    System.out.println("   Error reason: " + errorDetails.getReason());
                    System.out.println("   Error code: " + errorDetails.getErrorCode());
                    System.out.println("   Error message: " + errorDetails.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
// </snippet_single>