//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public readonly record struct LivenessDetectionError
{
    public readonly string? ResultId { get; init; }

    public readonly required FaceLivenessFailureReason LivenessError { get; init; }

    public readonly required FaceRecognitionFailureReason RecognitionError { get; init; }
}