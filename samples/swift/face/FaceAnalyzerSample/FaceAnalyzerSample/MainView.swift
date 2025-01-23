//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import Combine
import AzureAIVisionFaceUI

enum Page {
    case launch
    case settings
    case liveness
    case result
    case clientStart
    case imageSelection
}

class PageSelection: ObservableObject {
    @Published var current: Page = .launch

    func actionDidComplete() {
        current = .launch
    }
}

class SessionData: ObservableObject {
    @Published var resultId: String = ""
    @Published var resultDigest: String = ""
    @Published var referenceImageData: Data? = nil
    @Published var endpoint: String = "https://your.azure.endpoint.com"
    @Published var key: String = ""
    @Published var token: String? = nil
    @Published var sessionId: String? = nil
    @Published var referenceImageIsSelected = false
    @Published var isShowPhotoLibraryForReferenceImage = false
    @Published var isNetworkAvailable = true
    @Published var resultMessage = ""
    @Published var livenessWithVerify = false
    @Published var livenessMode: LivenessMode = .passive

    var settingsConfigured: Bool {
        !endpoint.isEmpty && !key.isEmpty
    }
}

struct MainView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @State var livenessDetectionResult: LivenessDetectionResult?

    var body: some View {
        ZStack {
            switch pageSelection.current {
            case .launch:
                LaunchView()

            case .settings:
                SettingsView()

            case .liveness:
                FaceLivenessDetectorView(result: $livenessDetectionResult,
                                         sessionAuthorizationToken: sessionData.token!)
                .onChange(of: livenessDetectionResult) { result in
                    if let result = result {
                        sessionData.resultMessage = sessionData.sessionResultMessage(livenessDetectionResult: result)
                        if case let .success(success) = result {
                            // Once the session is completed, the client does not receive the outcome whether face is live or spoof.
                            // You can query the result from your backend service by calling the sessions results API
                            // https://aka.ms/face/liveness-session/get-liveness-session-result
                            sessionData.resultId = success.resultId
                            sessionData.resultDigest = success.digest
                        } else if case let .failure(error) = result,
                                  let resultId = error.resultId {
                            sessionData.resultId = resultId
                        }
                        livenessDetectionResult = nil
                        withAnimation {
                            pageSelection.current = .result
                        }
                    }
                }

            case .result:
                ResultView()

            case .clientStart:
                ClientStartView()

            case .imageSelection:
                ImageSelectionView()
            }

            if !sessionData.isNetworkAvailable {
                VStack(alignment: .leading, spacing: 2) {
                    Spacer()
                    HStack() {
                        Text("Network unavailable. Results will show as Not computed.")
                        Spacer()
                    }
                    .foregroundColor(Color.white)
                    .padding(12)
                    .background(Color.red)
                    .cornerRadius(8)
                }.padding()
            }
        }
    }
}
