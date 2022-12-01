//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C# Image Analysis Samples
//
using System;
using System.Linq;

namespace ImageAnalysisSamples
{
    class Program
    {
        private static void PrintUsage()
        {
            Console.WriteLine("");
            Console.WriteLine(" To run the samples:");
            Console.WriteLine("");
            Console.WriteLine("   dotnet ImageAnalysisSamples.dll [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]");
            Console.WriteLine("");
            Console.WriteLine(" Where:");
            Console.WriteLine("   <your-key> - A computer vision key you get from your Azure portal.");
            Console.WriteLine("     It should be a 32-character HEX number.");
            Console.WriteLine("   <your-endpoint> - A computer vision endpoint you get from your Azure portal.");
            Console.WriteLine("     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com");
            Console.WriteLine("");
            Console.WriteLine(" As an alternative to specifying the above command line arguments, you can specify");
            Console.WriteLine($" these environment variables: {Secrets.EnvironmentVariableKey} and/or {Secrets.EnvironmentVariableEndpoint}.");
            Console.WriteLine("");
            Console.WriteLine(" To get this usage help, run:");
            Console.WriteLine("");
            Console.WriteLine("   dotnet ImageAnalysisSamples.dll --help|-h");
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
                Console.WriteLine(" 1. Get all results, including detailed results");
                Console.WriteLine(" 2. Get results using the Analyzed event");
                Console.WriteLine(" 3. Using frame source");
                Console.WriteLine("");
                Console.Write(" Your choice 1-3 (or 0 to exit): ");

                keyChar = Console.ReadKey().KeyChar;
                Console.WriteLine("\n");

                switch (keyChar)
                {
                    case '1':
                        Samples.GetAllResults(Secrets.Endpoint, Secrets.Key).Wait();
                        break;
                    case '2':
                        Samples.GetResultsUsingAnalyzedEvent(Secrets.Endpoint, Secrets.Key).Wait();
                        break;
                    case '3':
                        Samples.UsingFrameSource(Secrets.Endpoint, Secrets.Key); /*.Wait();*/
                        break;
                    case '0':
                        Console.WriteLine(" Exiting...");
                        break;
                    default:
                        Console.WriteLine(" Invalid input, choose again.");
                        break;
                }
            } while (keyChar != '0');
        }
    }
}
