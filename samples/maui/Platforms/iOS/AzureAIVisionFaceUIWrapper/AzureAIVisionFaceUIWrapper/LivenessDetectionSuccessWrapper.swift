//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

import AzureAIVisionFaceUI

@objc public class LivenessDetectionSuccessWrapper: NSObject {

    @objc public let resultId: String
    @objc public let digest: String

    init(success: LivenessDetectionSuccess) {
        resultId = success.resultId
        digest = success.digest
    }
}
