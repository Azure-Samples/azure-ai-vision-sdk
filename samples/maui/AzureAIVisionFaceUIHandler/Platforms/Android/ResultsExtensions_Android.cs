//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using Android.Content;

namespace AzureAIVisionFaceUIHandler;

public static class Extensions {
    public static LivenessDetectionResult? AsLivenessDetectionResult(this Intent intent)
    {
        if (intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.type"))
        {
            var type = intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.type");
            if (type == "success")
            {
                if (intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.resultId") &&
                    intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.resultId") is string resultId &&
                    intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.digest") &&
                    intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.digest") is string digest)
                {
                    return new LivenessDetectionResult
                    {
                        Success = new LivenessDetectionSuccess
                        {
                            ResultId = resultId,
                            Digest = digest
                        }
                    };
                }
                else
                {
                    throw new InvalidOperationException("Invalid LivenessDetectionSuccess");
                }
            }
            else if (type == "error")
            {
                string livenessError = string.Empty;
                string recognitionError = string.Empty;

                if (intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.livenessError") &&
                    intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.livenessError") is string livenessErrorJava)
                {
                    livenessError = livenessErrorJava;
                }
                if (intent.HasExtra("com.example.azureaivisionfaceuiwrapper.result.recognitionError") &&
                    intent.GetStringExtra("com.example.azureaivisionfaceuiwrapper.result.recognitionError") is string recognitionErrorJava)
                {
                    recognitionError = recognitionErrorJava;
                }

                var livenessErrorNet = livenessError.Replace("_", string.Empty);
                var recognitionErrorNet = recognitionError.Replace("_", string.Empty);

                if (Enum.TryParse(typeof(FaceLivenessFailureReason), livenessErrorNet, true, out var livenessErrorEnum) &&
                    Enum.TryParse(typeof(FaceRecognitionFailureReason), recognitionErrorNet, true, out var recognitionErrorEnum))
                {
                    return new LivenessDetectionResult
                    {
                        Error = new LivenessDetectionError
                        {
                            LivenessError = (FaceLivenessFailureReason)livenessErrorEnum,
                            RecognitionError = (FaceRecognitionFailureReason)recognitionErrorEnum
                        }
                    };
                }
                else
                {
                    throw new InvalidOperationException($"Invalid LivenessDetectionError livenessError={livenessError} recognitionError={recognitionError}");
                }
            }
        }

        return null;
    }
}