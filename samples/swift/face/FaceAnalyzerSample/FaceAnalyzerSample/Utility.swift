//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import AzureAIVisionFaceUI
import SwiftUI

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

class ErrorState: ObservableObject {
    @Published var message: String? = nil
    var dismissAction: (() -> Void)? = nil

    func show(_ message: String, onDismiss: (() -> Void)? = nil) {
        self.message = message
        self.dismissAction = onDismiss
    }

    func dismiss() {
        message = nil
        dismissAction?()
        dismissAction = nil
    }
}

struct ErrorAlert: ViewModifier {
    @ObservedObject var errorState: ErrorState

    func body(content: Content) -> some View {
        content
            .alert(isPresented: Binding<Bool>(
                get: { errorState.message != nil },
                set: { if !$0 { errorState.dismiss() } }
            )) {
                Alert(title: Text("Error"),
                      message: Text(errorState.message ?? ""),
                      dismissButton: .default(Text("OK")) {
                          errorState.dismiss()
                      })
            }
    }
}
