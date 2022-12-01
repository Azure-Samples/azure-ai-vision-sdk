//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Samples
//
#include "stdafx.h"
#include "secrets.h"

const std::string Secrets::EnvironmentVariableKey = "COMPUTER_VISION_KEY";
const std::string Secrets::EnvironmentVariableEndpoint = "COMPUTER_VISION_ENDPOINT";

std::string Secrets::key;
std::string Secrets::endpoint;

// Helper method to load and validate secrets. These secrets should never be compiled into source code.
// They should be loaded at run-time from a secure location. In these samples we first try to load
// secrets from command line arguments, and then from environment variables.
// This method returns true if the Computer Vision key & endpoint were loaded successfully and have
// a valid format.
bool Secrets::LoadSecretsSucceeded(std::vector<std::string> args)
{
    bool loadSucceeded = LoadKeySucceeded(args) && LoadEndpointSucceeded(args);

    if (loadSucceeded)
    {
        // Do not print the full value of your Computer Vision key to the console (or log file).
        // Here we only log the last 3 characters of the key.
        std::string maskedKey =  "*****************************" + key.substr(29, 3);

        std::cout << " Using computer vision key: " << maskedKey << std::endl;
        std::cout << " Using computer vision endpoint: " << endpoint << std::endl;
    }

    return loadSucceeded;
}

std::string Secrets::GetKey()
{
    return key;
}

std::string Secrets::GetEndpoint()
{
    return endpoint;
}

bool Secrets::LoadKeySucceeded(std::vector<std::string> args)
{
    // First try to read vision key from command-line arguments
    for(int i = 0; i < args.size()-1; i++)
    {
        if (args[i] == "--key" || args[i] == "-k")
        {
            key = args[i + 1];
            break;
        }
    }

    if (key.empty())
    {
        // If not found, try to read it from environment variable
        key = GetEnvironmentVariable(EnvironmentVariableKey);
    }

    return IsValidKey(key);
}

bool Secrets::LoadEndpointSucceeded(std::vector<std::string> args)
{
    // First try to read endpoint from command-line arguments
    for (int i = 0; i < args.size() - 1; i++)
    {
        if (args[i] == "--endpoint" || args[i] == "-e")
        {
            endpoint = args[i + 1];
            break;
        }
    }

    if (endpoint.empty())
    {
        // If not found, try to read it from environment variable
        endpoint = GetEnvironmentVariable(EnvironmentVariableEndpoint);
    }

    return IsValidEndpoint(endpoint);
}

// Validates the format of the Computer Vision Endpoint URL.
// Returns true if the endpoint is valid, false otherwise.
bool Secrets::IsValidEndpoint(std::string endpoint)
{
    if (endpoint.empty())
    {
        std::cout << " Error: Missing computer vision endpoint.\n\n";
        return false;
    }

    std::regex validEndpointFormat(R"(^https://\S+\.cognitiveservices\.azure\.com$)");
    if (!std::regex_match(endpoint, validEndpointFormat))
    {
        std::cout << " Error: Invalid value for computer vision endpoint: " << endpoint << std::endl;
        std::cout << " It should be in the form: https://<your-computer-vision-resource-name>.cognitiveservices.azure.com" << std::endl << std::endl;
        return false;
    }

    return true;
}

// Validates the format of the Computer Vision Key.
// Returns true if the key is valid, false otherwise.
bool Secrets::IsValidKey(std::string key)
{
    if (key.empty())
    {
        std::cout << " Error: Missing computer vision key.\n\n";
        return false;
    }

    std::regex validKeyFormat(R"(^[a-fA-F0-9]{32}$)");
    if (!std::regex_match(key, validKeyFormat))
    {
        std::cout << " Error: Invalid value for computer vision key: " << key << std::endl;
        std::cout << " It should be a 32-character HEX number.\n\n";
        return false;
    }

    return true;
}

std::string Secrets::GetEnvironmentVariable(const std::string name)
{
#if defined(_MSC_VER)
    size_t size = 0;
    char buffer[1024];
    getenv_s(&size, nullptr, 0, name.c_str());
    if (size > 0 && size < sizeof(buffer))
    {
        getenv_s(&size, buffer, size, name.c_str());
        return std::string{ buffer };
    }
#else
    const char* value = getenv(name.c_str());
    if (value != nullptr)
    {
        return std::string{ value };
    }
#endif
    return std::string{""};
}

