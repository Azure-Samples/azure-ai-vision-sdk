//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See https://aka.ms/csspeech/license for the full license information.
//
package azure.ai.vision.imageanalysis.samples;

import java.util.regex.Pattern;

/**
 * Helper class to load and validate secrets. These secrets should never be compiled into source code.
 * They should be loaded at run-time from a secure location. In these samples we first try to load
 * secrets from command line arguments, and then from environment variables.
 */
class Secrets {

    // These are the names of the environment variables you can set if you want to specify
    // secrets using environment variables instead of command-line arguments
    public static final String ENVIRONMENT_VARIABLE_KEY = "VISION_KEY";
    public static final String ENVIRONMENT_VARIABLE_ENDPOINT = "VISION_ENDPOINT";

    private static String key;
    private static String endpoint;

    /**
     * Returns the key to use for the Computer Vision service.
     * @return
     */
    public static String getKey() {
        return key;
    }

    /**
     * Returns the endpoint to use for the Computer Vision service.
     * @return
     */
    public static String getEndpoint() {
        return endpoint;
    }

    /**
     * Loads the secrets from command-line arguments or environment variables.
     * @param args
     * @return true if the secrets were loaded successfully, false otherwise.
     */
    public static boolean loadSucceeded(String[] args) {
        boolean loadSucceeded = loadKeySucceeded(args) && loadEndpointSucceeded(args);

        if (loadSucceeded) {
            String maskedKey = new String(new char[29]).replace('\0', '*') + key.substring(29, 32);

            System.out.println("\nUsing computer vision key: " + maskedKey);
            System.out.println("Using computer vision endpoint: " + endpoint);
        }

        return loadSucceeded;
    }

    private static boolean loadKeySucceeded(String[] args) {
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--key") || args[i].equals("-k")) {
                    if (i < args.length - 1) {
                        key = args[i + 1];
                        break;
                    }
                }
            }
        }

        if (key == null) {
            try {
                key = System.getenv(ENVIRONMENT_VARIABLE_KEY);
            } catch (Exception ignored) {
            }
        }

        return isValidKey(key);
    }

    private static boolean loadEndpointSucceeded(String[] args) {
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--endpoint") || args[i].equals("-e")) {
                    if (i < args.length - 1) {
                        endpoint = args[i + 1];
                        break;
                    }
                }
            }
        }

        if (endpoint == null) {
            try {
                endpoint = System.getenv(ENVIRONMENT_VARIABLE_ENDPOINT);
            } catch (Exception ignored) {
            }
        }

        return isValidEndpoint(endpoint);
    }

    private static boolean isValidEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            System.out.println("\nError: Missing computer vision endpoint.");
            return false;
        }

        Pattern pattern = Pattern.compile("^https://\\S+\\.cognitiveservices\\.azure\\.com/?$");
        if (!pattern.matcher(endpoint).matches()) {
            System.out.println("\nError: Invalid value for computer vision endpoint: " + endpoint);
            System.out.println("It should be in the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com");
            return false;
        }

        return true;
    }

    private static boolean isValidKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            System.out.println("\nError: Missing computer vision key.");
            return false;
        }

        Pattern pattern = Pattern.compile("^[a-fA-F0-9]{32}$");
        if (!pattern.matcher(key).matches()) {
            System.out.println("\nError: Invalid value for computer vision key: " + key);
            System.out.println("It should be a 32-character HEX number.");
            return false;
        }

        return true;
    }
}
