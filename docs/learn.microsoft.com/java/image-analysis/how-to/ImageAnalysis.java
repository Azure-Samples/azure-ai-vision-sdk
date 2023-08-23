// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// This code is integrated into this public document:
// https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-java

import java.net.URL;
import java.util.EnumSet;
import java.util.Arrays;

import com.azure.ai.vision.common.*;
import com.azure.ai.vision.imageanalysis.*;

public class ImageAnalysis {

    public static void main(String[] args) {

        try (
            // <vision_service_options>
            VisionServiceOptions serviceOptions = new VisionServiceOptions(
                new URL(System.getenv("VISION_ENDPOINT")),
                System.getenv("VISION_KEY"));
            // </vision_service_options>

            // <vision_source>
            VisionSource imageSource = VisionSource.fromUrl(
                new URL("https://learn.microsoft.com/azure/cognitive-services/computer-vision/media/quickstarts/presentation.png"));
            // </vision_source>

            // <visual_features>
            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions();

            ) {

            analysisOptions.setFeatures(EnumSet.of(
                    ImageAnalysisFeature.CROP_SUGGESTIONS,
                    ImageAnalysisFeature.CAPTION,
                    ImageAnalysisFeature.DENSE_CAPTIONS,
                    ImageAnalysisFeature.OBJECTS,
                    ImageAnalysisFeature.PEOPLE,
                    ImageAnalysisFeature.TEXT,
                    ImageAnalysisFeature.TAGS));
            // </visual_features>

            // <cropping_aspect_ratios>
            analysisOptions.setCroppingAspectRatios(Arrays.asList(0.9, 1.33));
            // </cropping_aspect_ratios>

            // <language>
            analysisOptions.setLanguage("en");
            // </language>

            // <gender_neutral_caption>
            analysisOptions.setGenderNeutralCaption(true);
            // </gender_neutral_caption>

            // <analyze>
            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);
                ImageAnalysisResult result = analyzer.analyze()) {

                if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {

                    System.out.println(" Image height = " + result.getImageHeight());
                    System.out.println(" Image width = " + result.getImageWidth());
                    System.out.println(" Model version = " + result.getModelVersion());

                    if (result.getCaption() != null) {
                        System.out.println(" Caption:");
                        System.out.println("   \"" + result.getCaption().getContent() + "\", Confidence "
                                + String.format("%.4f", result.getCaption().getConfidence()));
                    }

                    if (result.getDenseCaptions() != null) {
                        System.out.println(" Dense Captions:");
                        for (ContentCaption denseCaption : result.getDenseCaptions()) {
                            System.out.println("   \"" + denseCaption.getContent() + "\", Bounding box "
                                    + denseCaption.getBoundingBox() +
                                    ", Confidence " + String.format("%.4f", denseCaption.getConfidence()));
                        }
                    }

                    if (result.getObjects() != null) {
                        System.out.println(" Objects:");
                        for (DetectedObject detectedObject : result.getObjects()) {
                            System.out.println("   \"" + detectedObject.getName() + "\", Bounding box "
                                    + detectedObject.getBoundingBox() +
                                    ", Confidence " + String.format("%.4f", detectedObject.getConfidence()));
                        }
                    }

                    if (result.getTags() != null) {
                        System.out.println(" Tags:");
                        for (ContentTag tag : result.getTags()) {
                            System.out.println("   \"" + tag.getName() + "\", Confidence "
                                    + String.format("%.4f", tag.getConfidence()));
                        }
                    }

                    if (result.getPeople() != null) {
                        System.out.println(" People:");
                        for (DetectedPerson person : result.getPeople()) {
                            System.out.println("   Bounding box " + person.getBoundingBox() +
                                    ", Confidence " + String.format("%.4f", person.getConfidence()));
                        }
                    }

                    if (result.getCropSuggestions() != null) {
                        System.out.println(" Crop Suggestions:");
                        for (CropSuggestion cropSuggestion : result.getCropSuggestions()) {
                            System.out.println("   Aspect ratio " + cropSuggestion.getAspectRatio() +
                                    ": Crop suggestion " + cropSuggestion.getBoundingBox());
                        }
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

                    ImageAnalysisResultDetails resultDetails = ImageAnalysisResultDetails.fromResult(result);
                    System.out.println(" Result details:");
                    System.out.println("   Image ID = " + resultDetails.getImageId());
                    System.out.println("   Result ID = " + resultDetails.getResultId());
                    System.out.println("   Connection URL = " + resultDetails.getConnectionUrl());
                    System.out.println("   JSON result = " + resultDetails.getJsonResult());
                } else { // result.Reason == ImageAnalysisResultReason.Error
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
            // </analyze>

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
