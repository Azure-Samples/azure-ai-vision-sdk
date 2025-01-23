//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import AzureAIVisionFaceUI

extension SessionData {
    func sessionResultMessage(livenessDetectionResult: LivenessDetectionResult?) -> String {
        switch (livenessDetectionResult) {
        case .success:
            if let result = obtainResult(usingEndpoint: endpoint, key: key, withVerify: livenessWithVerify, sessionId: sessionId!) {
                
                var livenessResultString = "Liveness status: \(result.livenessDecision)\n"
                
                if let verifyResult = result.verifyResult
                {
                    livenessResultString += "Verify Is Identical: \(verifyResult.isIdentical)\n"
                    livenessResultString += "Verify Score: \(verifyResult.matchConfidence)\n"
                }
                return livenessResultString
            } else {
                return "Cannot retrieve result from service"
            }
        case .failure(let error):
            return """
                Liveness status: Failure
                Liveness Failure Reason: \(error.livenessError.localizedDescription)
                Verify Failure Reason: \(error.recognitionError.localizedDescription)
            """
        case .none:
            return "Liveness result: nil"
        }
    }
}
