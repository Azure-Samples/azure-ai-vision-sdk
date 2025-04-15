//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUIWrapper;

namespace AzureAIVisionFaceUIHandler;

public static class Extensions {
    public static LivenessDetectionResult Unwrap(this LivenessDetectionResultWrapper wrapper) {
        if (wrapper.Success != null && wrapper.Error == null)
        {
            return new LivenessDetectionResult
            {
                Success = new LivenessDetectionSuccess
                {
                    ResultId = wrapper.Success.ResultId,
                    Digest = wrapper.Success.Digest
                }
            };
        }
        else if (wrapper.Success == null && wrapper.Error != null)
        {
            var livenessErrorType = typeof(LivenessDetectionError).Assembly.GetType("AzureAIVisionFaceUIHandler.FaceLivenessFailureReason");
            var recognitionErrorType = typeof(LivenessDetectionError).Assembly.GetType("AzureAIVisionFaceUIHandler.FaceRecognitionFailureReason");

            return new LivenessDetectionResult {
                Error = new LivenessDetectionError {
                    ResultId = wrapper.Error.ResultId,
                    LivenessError = (FaceLivenessFailureReason)Enum.Parse(livenessErrorType, wrapper.Error.LivenessError.ToString(), true),
                    RecognitionError = (FaceRecognitionFailureReason)Enum.Parse(recognitionErrorType, wrapper.Error.RecognitionError.ToString(), true)
                }
            };
        }
        else
        {
            throw new InvalidOperationException("Invalid LivenessDetectionResultWrapper");
        }
    }
}