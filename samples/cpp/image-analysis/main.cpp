//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Samples
//
#include <iostream>
#include <string>
#include <vector>
#include "secrets.h"

extern void ImageAnalysisSample_Analyze(std::string endpoint, std::string key);
extern void ImageAnalysisSample_AnalyzeAsync(std::string endpoint, std::string key);
extern void ImageAnalysisSample_AnalyzeWithCustomModel(std::string endpoint, std::string key);
extern void ImageAnalysisSample_Segment(std::string endpoint, std::string key);

void PrintUsage()
{
    std::cout << std::endl;
    std::cout << " To run the samples:\n";
    std::cout << std::endl;
    std::cout << "   image-analysis-samples.exe [--key|-k <your-key>] [--endpoint|-e <your-endpoint>]\n";
    std::cout << std::endl;
    std::cout << " Where:\n";
    std::cout << "   <your-key> - A computer vision key you get from your Azure portal.\n";
    std::cout << "     It should be a 32-character HEX number.\n";
    std::cout << "   <your-endpoint> - A computer vision endpoint you get from your Azure portal.\n";
    std::cout << "     It should have the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com\n";
    std::cout << std::endl;
    std::cout << " As an alternative to specifying the above command line arguments, you can define\n";
    std::cout << " these environment variables: " << Secrets::EnvironmentVariableKey << " and " << Secrets::EnvironmentVariableEndpoint << "\n";
    std::cout << std::endl;
    std::cout << " To get this usage help, run:\n";
    std::cout << std::endl;
    std::cout << "   image-analysis-samples.exe --help|-h\n";
    std::cout << std::endl;
    std::cout.flush();
};


int main(int argc, char** argv)
{
    std::vector<std::string> args(argv, argv + argc);

    std::cout << std::endl;
    std::cout << " Azure AI Vision SDK - Image Analysis Samples\n";
    std::cout << std::endl;

    for (const std::string& arg : args)
    {
        if (arg == "--help" || arg == "-h")
        {
            PrintUsage();
            return 0;
        }
    }
    
    if (!Secrets::LoadSecretsSucceeded(args))
    {
        PrintUsage();
        return 0;
    }

    std::string input;
    do
    {
        std::cout << std::endl;
        std::cout << " Please choose one of the following samples:\n";
        std::cout << std::endl;
        std::cout << " 1. Analyze image from file, all features, synchronous (blocking)\n";
        std::cout << " 2. Analyze image from URL, asynchronous (non-blocking)\n";
        std::cout << " 3. Analyze image using a custom-trained model\n";
        std::cout << " 4. Background removal\n";
        std::cout << std::endl;
        std::cout << " Enter your choice 1-4 (or 0 to exit) and press enter:\n";
        std::cout.flush();

        input.clear();
        std::getline(std::cin, input);

        try
        {
            switch (input[0])
            {
            case '1':
                ImageAnalysisSample_Analyze(Secrets::GetEndpoint(), Secrets::GetKey());
                break;
            case '2':
                ImageAnalysisSample_AnalyzeAsync(Secrets::GetEndpoint(), Secrets::GetKey());
                break;
            case '3':
                ImageAnalysisSample_AnalyzeWithCustomModel(Secrets::GetEndpoint(), Secrets::GetKey());
                break;
            case '4':
                ImageAnalysisSample_Segment(Secrets::GetEndpoint(), Secrets::GetKey());
                break;
            case '0':
                std::cout << " Exiting...\n";
                break;
            default:
                std::cout << " Invalid selection, choose again.\n";
                break;
            }
        }
        catch (std::exception& e)
        {
            std::cout << e.what() << std::endl;
        }
    } while (input[0] != '0');
}
