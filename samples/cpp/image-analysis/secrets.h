//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// Azure AI Vision SDK -- C++ Image Analysis Samples
//
#include "stdafx.h"

class Secrets
{
public:
    static const std::string EnvironmentVariableKey;
    static const std::string EnvironmentVariableEndpoint;

    static std::string GetKey();
    static std::string GetEndpoint();

    static bool LoadSecretsSucceeded(std::vector<std::string> args);

private:
    static std::string key;
    static std::string endpoint;

    static bool LoadKeySucceeded(std::vector<std::string> args);
    static bool LoadEndpointSucceeded(std::vector<std::string> args);
    static bool IsValidEndpoint(std::string endpoint);
    static bool IsValidKey(std::string key);
    static std::string GetEnvironmentVariable(const std::string name);
};
