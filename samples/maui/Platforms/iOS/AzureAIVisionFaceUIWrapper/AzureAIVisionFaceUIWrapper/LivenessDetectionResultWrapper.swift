//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

import AzureAIVisionFaceUI

@objc public class LivenessDetectionResultWrapper: NSObject {
    
    @objc public let success: LivenessDetectionSuccessWrapper?
    @objc public let error: LivenessDetectionErrorWrapper?
    
    init(result: LivenessDetectionResult) {
        switch result {
        case .success(let success):
            self.success = LivenessDetectionSuccessWrapper(success: success)
            self.error = nil
        case .failure(let error):
            self.success = nil
            self.error = LivenessDetectionErrorWrapper(error: error)
        }
    }
}
