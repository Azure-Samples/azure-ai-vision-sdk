//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C# Image Analysis Samples
//
using System;
using System.Linq;

class Program
{
    private static void PrintUsage()
    {
        Console.WriteLine("");
        Console.WriteLine(" To run the samples:");
        Console.WriteLine("");
        Console.WriteLine("   dotnet image-analysis-samples.dll [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]");
        Console.WriteLine("");
        Console.WriteLine(" Where:");
        Console.WriteLine("   <your-key> - A computer vision key you get from your Azure portal.");
        Console.WriteLine("     It should be a 32-character HEX number.");
        Console.WriteLine("   <your-endpoint> - A computer vision endpoint you get from your Azure portal.");
        Console.WriteLine("     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com");
        Console.WriteLine("");
        Console.WriteLine(" As an alternative to specifying the above command line arguments, you can define");
        Console.WriteLine($" these environment variables: {Secrets.EnvironmentVariableKey} and/or {Secrets.EnvironmentVariableEndpoint}.");
        Console.WriteLine("");
        Console.WriteLine(" To get this usage help, run:");
        Console.WriteLine("");
        Console.WriteLine("   dotnet image-analysis-samples.dll --help|-h");
        Console.WriteLine("");
    }

    static void Main(string[] args)
    {
        char keyChar;

        Console.WriteLine("");
        Console.WriteLine(" Azure AI Vision SDK - Image Analysis Samples");
        Console.WriteLine("");

        if (args.Contains("--help") || args.Contains("-h") || !Secrets.LoadSucceeded(args))
        {
            PrintUsage();
            return;
        }

        do
        {
            Console.WriteLine("");
            Console.WriteLine(" Please choose one of the following samples:");
            Console.WriteLine("");
            Console.WriteLine(" 1. Analyze image from file, all features, synchronous (blocking)");
            Console.WriteLine(" 2. Analyze image from URL, asynchronous (non-blocking)");
            Console.WriteLine(" 3. Analyze image from memory buffer, synchronous (blocking)");
            Console.WriteLine(" 4. Analyze image using a custom-trained model");
            Console.WriteLine(" 5. Background removal");
            Console.WriteLine("");
            Console.Write(" Your choice 1-4 (or 0 to exit): ");

            keyChar = Console.ReadKey().KeyChar;
            Console.WriteLine("\n");

            try
            {
                switch (keyChar)
                {
                    case '1':
                        Samples.ImageAnalysisSample_Analyze_File(Secrets.Endpoint, Secrets.Key);
                        break;
                    case '2':
                        Samples.ImageAnalysisSample_AnalyzeAsync_Url(Secrets.Endpoint, Secrets.Key).Wait();
                        break;
                    case '3':
                        Samples.ImageAnalysisSample_Analyze_Buffer(Secrets.Endpoint, Secrets.Key);
                        break;
                    case '4':
                        Samples.ImageAnalysisSample_Analyze_WithCustomModel(Secrets.Endpoint, Secrets.Key);
                        break;
                    case '5':
                        Samples.ImageAnalysisSample_Segment(Secrets.Endpoint, Secrets.Key);
                        break;
                    case '0':
                        Console.WriteLine(" Exiting...");
                        break;
                    default:
                        Console.WriteLine(" Invalid selection, choose again.");
                        break;
                }
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        } while (keyChar != '0');
    }
}
