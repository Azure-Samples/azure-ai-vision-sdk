//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public readonly record struct LivenessDetectionResult
{
    public readonly LivenessDetectionSuccess? Success { get; init; }

    public readonly LivenessDetectionError? Error { get; init; }
}