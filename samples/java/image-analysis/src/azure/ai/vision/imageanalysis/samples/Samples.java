//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See https://aka.ms/csspeech/license for the full license information.
//
package azure.ai.vision.imageanalysis.samples;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;

import reactor.core.publisher.Mono;

import com.azure.ai.vision.common.*;
import com.azure.ai.vision.imageanalysis.*;

/**
 * This sample application shows how to use features of the Azure AI Vision
 * Image Analysis SDK for Java
 */
public class Samples {

    /**
     * This sample does analysis on an image file using all visual features
     * and a synchronous (blocking) call. It prints the results to the console,
     * including the detailed results.
     * 
     * @param endpoint The Azure endpoint for the Computer Vision service
     * @param key      The Azure key for the Computer Vision service
     */
    public static void analyzeImageFromFile(String endpoint, String key) {
        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(new URL(endpoint), key);

            // Specify the image file on disk to analyze. sample.jpg is a good example to show most features.
            // Alternatively, specify an image URL (e.g. https://aka.ms/azai/vision/image-analysis-sample.jpg)
            // or a memory buffer containing the image. see:
            // https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-java#select-the-image-to-analyze
            VisionSource imageSource = VisionSource.fromFile("sample.jpg");

            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions()) {

            // Mandatory. You must set one or more features to analyze. Here we use the full set of features.
            // Note that 'Caption' and 'DenseCaptions' are only supported in Azure GPU regions (East US, France Central, Korea Central,
            // North Europe, Southeast Asia, West Europe, West US). Remove 'Caption' and 'DenseCaptions' from the list below if your
            // Computer Vision key is not from one of those regions.
            analysisOptions.setFeatures(EnumSet.of(
                    ImageAnalysisFeature.CROP_SUGGESTIONS,
                    ImageAnalysisFeature.CAPTION,
                    ImageAnalysisFeature.DENSE_CAPTIONS,
                    ImageAnalysisFeature.OBJECTS,
                    ImageAnalysisFeature.PEOPLE,
                    ImageAnalysisFeature.TEXT,
                    ImageAnalysisFeature.TAGS));
            
            // Optional, and only relevant when you select ImageAnalysisFeature.CropSuggestions.
            // Define one or more aspect ratios for the desired cropping. Each aspect ratio needs to be in the range [0.75, 1.8].
            // If you do not set this, the service will return one crop suggestion with the aspect ratio it sees fit.
            analysisOptions.setCroppingAspectRatios(Arrays.asList(0.9, 1.33));
            
            // Optional. Default is "en" for English. See https://aka.ms/cv-languages for a list of supported
            // language codes and which visual features are supported for each language.
            analysisOptions.setLanguage("en");
            
            // Optional. Default is "latest".
            analysisOptions.setModelVersion("latest");
            
            // Optional, and only relevant when you select ImageAnalysisFeature.Caption.
            // Set this to "true" to get a gender neutral caption (the default is "false").
            analysisOptions.setGenderNeutralCaption(true);

            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions)) {
                System.out.println(" Please wait for image analysis results...\n");

                // This call creates the network connection and blocks until Image Analysis results
                // return (or an error occurred). Note that there is also an asynchronous (non-blocking)
                // version of this method: analyzer.analyzeAsync().
                try(
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
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This sample does analysis on an image URL, using an asynchronous
     * (non-blocking) call to analyze one visual feature (Tags)
     * 
     * @param endpoint The Azure endpoint for the Computer Vision service
     * @param key      The Azure key for the Computer Vision service
     */
    public static void analyzeImageFromUrlAsync(String endpoint, String key) {
        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(new URL(endpoint), key);

            VisionSource imageSource = VisionSource.fromUrl(
                new URL("https://aka.ms/azai/vision/image-analysis-sample.jpg"));

            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions();) {

            analysisOptions.setFeatures(EnumSet.of(ImageAnalysisFeature.TAGS));

            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions)) {

                Mono<ImageAnalysisResult> analysisResultMono = analyzer.analyzeAsync();

                analysisResultMono.subscribe(result -> {
                    if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {
                        System.out.println(" Tags:");
                        for (ContentTag tag : result.getTags()) {
                            System.out.println(
                                    String.format("   \"%s\", Confidence %.4f", tag.getName(), tag.getConfidence()));
                        }
                    } else {
                        System.out.println(" Analysis failed.");
                        ImageAnalysisErrorDetails errorDetails = ImageAnalysisErrorDetails.fromResult(result);
                        System.out.println("   Error reason: " + errorDetails.getReason());
                        System.out.println("   Error code: " + errorDetails.getErrorCode());
                        System.out.println("   Error message: " + errorDetails.getMessage());
                        System.out.println(" Did you set the computer vision endpoint and key?");
                    }
                });

                analysisResultMono.block().close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This sample does analysis on an image from an input memory buffer, using synchronous (blocking)
     * call to analyze one visual feature (Caption).
     * 
     * @param endpoint The Azure endpoint for the Computer Vision service
     * @param key      The Azure key for the Computer Vision service
     */
    public static void analyzeImageFromBuffer(String endpoint, String key) {

        try {
            // This sample assumes you have an image in a memory buffer. Here we simply load it from file.
            final ByteBuffer imageBuffer = fileToByteBuffer("sample.jpg");

            try (
                // Create an ImageSourceBuffer, and copy the input image into it
                ImageSourceBuffer imageSourceBuffer = new ImageSourceBuffer();
                ImageWriter imageWriter = imageSourceBuffer.getWriter()) {
                imageWriter.write(imageBuffer);

                try (
                    // Create your VisionSource from the ImageSourceBuffer
                    VisionSource imageSource = VisionSource.fromImageSourceBuffer(imageSourceBuffer);

                    ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions()) {
                    analysisOptions.setFeatures(EnumSet.of(ImageAnalysisFeature.CAPTION));

                    try (
                        VisionServiceOptions serviceOptions = new VisionServiceOptions(new URL(endpoint), key);
                        ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions);
                        ImageAnalysisResult result = analyzer.analyze()) {

                        if (result.getReason() == ImageAnalysisResultReason.ANALYZED) {

                            if (result.getCaption() != null) {
                                System.out.println(" Caption:");
                                System.out.println("   \"" + result.getCaption().getContent() + "\", Confidence "
                                        + String.format("%.4f", result.getCaption().getConfidence()));
                            }

                        } else { // result.Reason == ImageAnalysisResultReason.Error

                            ImageAnalysisErrorDetails errorDetails = ImageAnalysisErrorDetails.fromResult(result);
                            System.out.println(" Analysis failed.");
                            System.out.println("   Error reason: " + errorDetails.getReason());
                            System.out.println("   Error code: " + errorDetails.getErrorCode());
                            System.out.println("   Error message: " + errorDetails.getMessage());
                            System.out.println(" Did you set the computer vision endpoint and key?");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This sample does analysis on an image file using a given custom-trained
     * model, and shows how to get the detected objects and/or tags.
     * 
     * @param endpoint The Azure endpoint for the Computer Vision service
     * @param key      The Azure key for the Computer Vision service
     */
    public static void analyzeImageWithCustomModel(String endpoint, String key) {
        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(new URL(endpoint), key);

            VisionSource visionSource = VisionSource.fromFile("sample.jpg");

            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions();) {

            // Set the model name to the name of your custom model
            analysisOptions.setModelName("YourCustomModelNameHere");

            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, visionSource, analysisOptions);

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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This sample does segmentation of an input image and writes the
     * resulting background-removed image or foreground matte image to disk.
     * 
     * @param endpoint The Azure endpoint for the Computer Vision service
     * @param key      The Azure key for the Computer Vision service
     */
    public static void backgroundRemoval(String endpoint, String key) {
        try (
            VisionServiceOptions serviceOptions = new VisionServiceOptions(new URL(endpoint), key);

            // Specify the image file on disk to analyze. sample.jpg is a good example to show most features.
            // Alternatively, specify an image URL (e.g. https://aka.ms/azai/vision/image-analysis-sample.jpg)
            // or a memory buffer containing the image. see:
            // https://learn.microsoft.com/azure/ai-services/computer-vision/how-to/call-analyze-image-40?pivots=programming-language-java#select-the-image-to-analyze
            VisionSource imageSource = VisionSource.fromFile("sample.jpg");

            ImageAnalysisOptions analysisOptions = new ImageAnalysisOptions()) {

            // Set one of two segmentation options: 'ImageSegmentationMode.BACKGROUND_REMOVAL' or
            // 'ImageSegmentationMode.FOREGROUND_MATTING'
            analysisOptions.setSegmentationMode(ImageSegmentationMode.BACKGROUND_REMOVAL);

            try (
                ImageAnalyzer analyzer = new ImageAnalyzer(serviceOptions, imageSource, analysisOptions)) {

                System.out.println(" Please wait for image analysis results...\n");

                // This call creates the network connection and blocks until Image Analysis
                // results return (or an error occurred). Note that there is also an
                // asynchronous
                // (non-blocking) version of this method: analyzer.analyzeAsync().
                try (
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
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to return a ByteBuffer containing content
     * read from file on disk.
     * 
     * @param path The full-path file name of the file to read.
     */
    private static ByteBuffer fileToByteBuffer(String path) throws java.io.FileNotFoundException, java.io.IOException {
        InputStream inputStream = new FileInputStream(path);
        byte[] bytes = inputStream.readAllBytes();
        inputStream.close();
        return ByteBuffer.wrap(bytes);
    }
}