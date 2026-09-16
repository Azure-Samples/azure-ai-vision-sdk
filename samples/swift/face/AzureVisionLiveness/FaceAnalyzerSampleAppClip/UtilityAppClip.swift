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
            return "Check complete."
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

struct LinkAuthProgressOverlay: ViewModifier {
    @ObservedObject private var progress: LinkAuthProgress = .shared

    func body(content: Content) -> some View {
        ZStack {
            content
            if progress.isActive {
                Color.black.opacity(0.4)
                    .edgesIgnoringSafeArea(.all)
                    .allowsHitTesting(true)
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    Text(progress.stage)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
                .background(Color.black.opacity(0.7))
                .cornerRadius(12)
            }
        }
    }
}
