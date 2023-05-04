//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C# Image Analysis Samples
//
using System;
using System.Text.RegularExpressions;

// Helper class to load and validate secrets. These secrets should never be compiled into source code.
// They should be loaded at run-time from a secure location. In these samples we first try to load
// secrets from command line arguments, and then from environment variables.
class Secrets
{
    // These are the names of the environment variables you can set if you want to specify
    // secrets using environment variables instead of command-line arguments
    public static readonly string EnvironmentVariableKey = "VISION_KEY";
    public static readonly string EnvironmentVariableEndpoint = "VISION_ENDPOINT";

    // Get these properties only after a call to LoadSucceed returned true
    public static string Key { get; private set; }
    public static string Endpoint { get; private set; }

    // Call this method first to load the secrets.
    // This method returns true if the Computer Vision key & endpoint were loaded successfully and have
    // a valid format.
    public static bool LoadSucceeded(string[] args)
    {
        bool loadSucceeded = LoadKeySucceeded(args) && LoadEndpointSucceeded(args);

        if (loadSucceeded)
        {
            // Do not print the full value of your Computer Vision key to the console (or log file).
            // Here we only log the last 3 characters of the key.
            string maskedKey =  new String('*', 29) + Key.Substring(28, 3);

            Console.WriteLine(" Using computer vision key: " + maskedKey);
            Console.WriteLine(" Using computer vision endpoint: " + Endpoint);
        }

        return loadSucceeded;
    }

    // Returns true of the Computer Vision Key exists and is valid. False otherwise.
    private static bool LoadKeySucceeded(string[] args)
    {
        // First try to read vision key from command-line arguments
        if (args != null && args.Length > 0)
        {
            foreach (string flag in new string[] { "--key", "-k" })
            {
                int i = Array.IndexOf(args, flag);
                if (i >= 0 && i < args.Length - 1)
                {
                    Key = args[i + 1];
                    break;
                }
            }
        }

        // If not found, try to read it from environment variable
        if (Key == null)
        {
            try
            {
                Key = Environment.GetEnvironmentVariable(EnvironmentVariableKey);
            }
            catch
            {
                // Ignore
            }
        }

        return IsValidKey(Key);
    }

    // Returns true of the Computer Vision Endpoint URL exists and is valid. False otherwise.
    private static bool LoadEndpointSucceeded(string[] args)
    {
        // First try to read endpoint from command-line arguments
        if (args != null && args.Length > 0)
        {
            foreach (string flag in new string[] { "--endpoint", "-e" })
            {
                int i = Array.IndexOf(args, flag);
                if (i >= 0 && i < args.Length - 1)
                {
                    Endpoint = args[i + 1];
                    break;
                }
            }
        }

        // If not found, try to read it from environment variable
        if (Endpoint == null)
        {
            try
            {
                Endpoint = Environment.GetEnvironmentVariable(EnvironmentVariableEndpoint);
            }
            catch
            {
                // Ignore
            }
        }

        return IsValidEndpoint(Endpoint);
    }

    // Validates the format of the Computer Vision Endpoint URL.
    // Returns true if the endpoint is valid, false otherwise.
    private static bool IsValidEndpoint(string endpoint)
    {
        if (String.IsNullOrEmpty(endpoint))
        {
            Console.WriteLine(" Error: Missing computer vision endpoint.");
            Console.WriteLine("");
            return false;
        }

        if (!Regex.IsMatch(endpoint, @"^https://\S+\.cognitiveservices\.azure\.com/?$"))
        {
            Console.WriteLine($" Error: Invalid value for computer vision endpoint: {endpoint}.");
            Console.WriteLine(" It should be in the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com");
            Console.WriteLine("");
            return false;
        }

        return true;
    }

    // Validates the format of the Computer Vision Key.
    // Returns true if the key is valid, false otherwise.
    private static bool IsValidKey(string key)
    {
        if (String.IsNullOrWhiteSpace(key))
        {
            Console.WriteLine(" Error: Missing computer vision key.");
            Console.WriteLine("");
            return false;
        }

        if (!Regex.IsMatch(key, @"^[a-fA-F0-9]{32}$"))
        {
            Console.WriteLine($" Error: Invalid value for computer vision key: {key}.");
            Console.WriteLine(" It should be a 32-character HEX number.");
            Console.WriteLine("");
            return false;
        }

        return true;
    }
}
