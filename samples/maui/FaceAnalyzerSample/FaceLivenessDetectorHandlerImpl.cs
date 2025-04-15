//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUIHandler;

namespace FaceAnalyzerSample;

public partial class FaceLivenessDetectorHandlerImpl: FaceLivenessDetectorHandler
{
    FaceAPIToken? session;

    override public FaceAPIToken GetSessionAuthorizationToken()
    {
        session = FaceAPI.GetToken();

        if (session is FaceAPIToken token)
        {
            return token;
        }
        else
        {
            throw new Exception("Failed to get session token");
        }
    }
}
