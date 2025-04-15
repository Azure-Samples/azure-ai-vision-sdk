//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

import AzureAIVisionFaceUI

@objc public class LivenessDetectionErrorWrapper: NSObject {

    @objc public let resultId: String?
    @objc public let livenessError: LivenessError
    @objc public let recognitionError: RecognitionError

    init(error: LivenessDetectionError) {
        resultId = error.resultId
        livenessError = error.livenessError
        recognitionError = error.recognitionError
    }
}
