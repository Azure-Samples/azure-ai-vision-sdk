//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUIHandler;

namespace FaceAnalyzerSample;

public partial class ResultPage : ContentPage
{
    public ResultPage(LivenessDetectionResult result, string sessionId)
    {
        InitializeComponent();

        if (result.Error is LivenessDetectionError error)
        {
            if (error.ResultId is string resultId)
            {
                IdLabel.Text = resultId;
            }

            ResultLabel.Text = $"Liveness Error: {error.LivenessError}\nRecognition Error: {error.RecognitionError}";
        }
        else if (result.Success is LivenessDetectionSuccess success)
        {
            IdLabel.Text = $"Result Id: {success.ResultId}";
            DigestLabel.Text = $"Digest: {success.Digest}";
            ResultLabel.Text = $"Liveness Decision: {FaceAPI.GetResult(sessionId) ?? "Error contacting server"}";
        }
    }

    public ResultPage(string sessionId)
    {
        InitializeComponent();

        ResultLabel.Text = $"Liveness Decision: {FaceAPI.GetResult(sessionId) ?? "Error contacting server"}";
    }
}