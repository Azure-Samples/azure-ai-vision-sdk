//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AzureAIVisionFaceDeviceAttestation

struct ResultView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @Environment(\.openURL) private var openURL
    var body: some View {
        ZStack {
            Spacer()
            Rectangle()
                .fill(Color.white)
                .edgesIgnoringSafeArea(.all)
            Spacer()
            VStack() {
                Spacer()
                Text(sessionData.resultMessage)
                    .fixedSize(horizontal: false, vertical: false)
                    .frame(alignment: .topLeading)
                    .font(.system(size: 24))
                    .lineLimit(nil)
                    .foregroundColor(Color.black)
                    .padding(.top, -2.5)
                    .accessibilityIdentifier("livenessResult")
                    .onAppear{
                        // Mirror Android's ResultScreen.LaunchedEffect: post the
                        // attestation digest before handing control back via
                        // callbackUrl. Gate on there being an active attestation
                        // session (created during startSession) — that's the
                        // iOS equivalent of Android's FaceSessionToken.quickLink
                        // check. Same wiring as the full app's ResultView.
                        let digest = sessionData.resultDigest
                        let sessionId = sessionData.sessionId
                        let clientId = sessionData.deviceCorrelationIdInClient
                        let callbackUrl = sessionData.callbackUrl
                        let livenessHost = sessionData.livenessHost
                        sessionData.callbackUrl = nil

                        Task {
                            if !digest.isEmpty,
                               sessionId?.isEmpty == false,
                               clientId?.isEmpty == false,
                               let session = await DeviceAttestation.shared.currentSession() {
                                switch await session.submitLivenessDigest(digest) {
                                case .success:
                                    print("Digest posted successfully")
                                case .error(let code, let message):
                                    print("Failed to post digest: \(code) - \(message)")
                                case .exception(let error):
                                    print("Exception posting digest: \(error)")
                                }
                            }

                            if let url = sanitizedCallbackURL(callbackUrl, allowedHost: livenessHost) {
                                await MainActor.run {
                                    openURL(url)
                                }
                            }
                        }
                    }
                Spacer()
            }

        }
    }

}
