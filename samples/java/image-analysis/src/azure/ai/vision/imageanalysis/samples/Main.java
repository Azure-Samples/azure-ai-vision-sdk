//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
package azure.ai.vision.imageanalysis.samples;

import java.util.Arrays;
import java.util.Scanner;

/**
 * This sample application shows how to use features of the Azure AI Vision Image Analysis SDK for Java
 */
public class Main {

    @SuppressWarnings("resource") // Scanner
    public static void main(String[] args){

        try {
            System.out.println("\nAzure AI Vision SDK - Image Analysis Samples");

            if (containsHelpOption(args) || !Secrets.loadSucceeded(args)) {
                printUsage();
                return;
            }

            boolean continueRunning = true;
            do {
                System.out.println("\nPlease choose one of the following samples:");
                System.out.println(" 1. Analyze image from file, all features, synchronous (blocking)");
                System.out.println(" 2. Analyze image from URL, asynchronous (non-blocking)");
                System.out.println(" 3. Analyze image from memory buffer, synchronous (blocking)");
                System.out.println(" 4. Analyze image using a custom-trained model");
                System.out.println(" 5. Background removal");
                System.out.print("\nYour choice 1-4 (or 0 to exit): ");
    
                String input = getInputArgument(args);
                if (input == null || input.isEmpty()) {
                    input = new Scanner(System.in).nextLine();
                } else {
                    continueRunning = false; // Input argument provided, exit after running once
                }

                int choice = Integer.parseInt(input);

                switch (choice)
                {
                case 1:
                    Samples.analyzeImageFromFile(Secrets.getEndpoint(), Secrets.getKey());
                    break;
                case 2:
                    Samples.analyzeImageFromUrlAsync(Secrets.getEndpoint(), Secrets.getKey());
                    break;
                case 3:
                    Samples.analyzeImageFromBuffer(Secrets.getEndpoint(), Secrets.getKey());
                    break;
                case 4:
                    Samples.analyzeImageWithCustomModel(Secrets.getEndpoint(), Secrets.getKey());
                    break;
                case 5:
                    Samples.backgroundRemoval(Secrets.getEndpoint(), Secrets.getKey());
                    break;
                case 0:
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid choice!");
                    break;
                }
            } while (continueRunning);
        }
        catch (Exception e) {
            System.err.println("Exception caught: " + e.toString());
            System.exit(2);
        }

        System.exit(0);
    }

    private static boolean containsHelpOption(String[] args) {
        return Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h");
    }

    private static String getInputArgument(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--input")) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("To run the samples:");
        System.out.println();
        System.out.println("   java -jar ./target/image-analysis-samples-snapshot-jar-with-dependencies.jar [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]");
        System.out.println();
        System.out.println("Where:");
        System.out.println("   <your-key> - A computer vision key you get from your Azure portal.");
        System.out.println("     It should be a 32-character HEX number.");
        System.out.println("   <your-endpoint> - A computer vision endpoint you get from your Azure portal.");
        System.out.println("     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com");
        System.out.println();
        System.out.println("As an alternative to specifying the command line arguments, you can define");
        System.out.println(String.format("these environment variables: %s and/or %s.", Secrets.ENVIRONMENT_VARIABLE_KEY, Secrets.ENVIRONMENT_VARIABLE_ENDPOINT));
        System.out.println();
        System.out.println("To get this usage help, run:");
        System.out.println();
        System.out.println("   java -jar ./target/image-analysis-samples-snapshot-jar-with-dependencies.jar --help|-h");
        System.out.println();
    }
}