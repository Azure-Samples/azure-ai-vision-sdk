//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import Combine
import AzureAIVisionFace

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
    @Published var referenceImage: UIImage? = nil
    @Published var endpoint: String = "https://your.azure.endpoint.com"
    @Published var key: String = ""
    @Published var token: String? = nil
    @Published var referenceImageIsSelected = false
    @Published var isShowPhotoLibraryForReferenceImage = false
    @Published var isNetworkAvailable = true
    @Published var resultMessage = ""
    @Published var livenessWithVerify = false
    @Published var sendResultsToClient = true

    var settingsConfigured: Bool {
        !endpoint.isEmpty && !key.isEmpty
    }
}

struct MainView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    
    var body: some View {
        ZStack {
            switch pageSelection.current {
            case .launch:
                LaunchView()

            case .settings:
                SettingsView()

            case .liveness:
                LivenessView(sessionAuthorizationToken: sessionData.token!,
                             withVerification: sessionData.livenessWithVerify,
                             referenceImage: sessionData.referenceImage,
                             completionHandler: { resultMessage, resultId, resultDigest in
                                sessionData.resultMessage = resultMessage
                                sessionData.resultId = resultId
                                sessionData.resultDigest = resultDigest
                                DispatchQueue.main.async {
                                    withAnimation {
                                        pageSelection.current = .result
                                    }
                                }
                            })

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
