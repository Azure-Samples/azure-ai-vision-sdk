//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public readonly record struct LivenessDetectionSuccess
{
    public readonly required string ResultId { get; init; }

    public readonly required string Digest { get; init; }
}