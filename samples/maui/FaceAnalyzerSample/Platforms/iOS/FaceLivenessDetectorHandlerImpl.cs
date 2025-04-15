//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUIHandler;
using AzureAIVisionFaceUIWrapper;
using Foundation;

namespace FaceAnalyzerSample;

public class FaceLivenessDetectorWrapperDelegate: NSObject, IFaceLivenessDetectorWrapperDelegate
{
    private string sessionId;

    public FaceLivenessDetectorWrapperDelegate(string sessionId)
    {
        this.sessionId = sessionId;
    }

    public void ResultHasChangedWithResult(LivenessDetectionResultWrapper result)
    {
        var unwrappedResult = result.Unwrap();
        var page = new ResultPage(unwrappedResult, sessionId);
        Shell.Current.Navigation.PushAsync(page);
    }
}

public partial class FaceLivenessDetectorHandlerImpl: FaceLivenessDetectorHandler
{

    override public IFaceLivenessDetectorWrapperDelegate GetDelegate()
    {
        return new FaceLivenessDetectorWrapperDelegate(session?.SessionId ?? string.Empty);
    }
}