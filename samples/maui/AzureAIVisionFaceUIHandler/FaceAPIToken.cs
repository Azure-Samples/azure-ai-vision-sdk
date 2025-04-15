//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public readonly record struct FaceAPIToken
{
    public required string SessionId { get; init; }
    public required string AuthToken { get; init; }
}