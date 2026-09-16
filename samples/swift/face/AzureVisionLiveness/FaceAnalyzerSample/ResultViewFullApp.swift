//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AzureAIVisionFaceDeviceAttestation

struct ResultView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @ObservedObject private var appLinkState = AppLinkState.shared
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
                Text(appLinkState.isFromAppLink ? "Check Complete" : sessionData.resultMessage)
                    .fixedSize(horizontal: false, vertical: false)
                    .frame(alignment: .topLeading)
                    .font(.system(size: 24))
                    .lineLimit(nil)
                    .foregroundColor(Color.black)
                    .padding(.top, -2.5)
                    .accessibilityIdentifier("livenessResult")
                    .onAppear{
                        // Mirror Android's ResultScreen.LaunchedEffect: when the
                        // liveness SDK produced a digest and we're in the
                        // QuickLink/cert-based flow, post the digest to the
                        // server BEFORE handing control back via callbackUrl.
                        // Gate on there being an active attestation session
                        // (created during startSession) — that's the iOS equivalent of
                        // Android's FaceSessionToken.quickLink == true check.
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
                // AppLink flow hands control back via callbackUrl — no
                // Retry / Continue buttons; show them only for sessions that
                // were started via the manual launch buttons.
                if(!appLinkState.isFromAppLink && sessionData.sessionId != nil){
                    HStack() {
                        Button(action: retry, label: {
                            Text("Retry")
                                .foregroundColor(.red)
                                .padding()
                        }).accessibilityIdentifier("retryButton")
                        Spacer()
                        Button(action: doneReviewInDemo, label: {
                            Text("Continue")
                                .padding()
                        }).accessibilityIdentifier("continueButton")
                    }
                }
            }

        }
    }

    func retry() {
        withAnimation {
            pageSelection.current = .liveness
        }
    }

    func doneReviewInDemo() {
        withAnimation {
            pageSelection.current = .launch
            sessionData.token = nil
            sessionData.sessionId = nil
            AppLinkState.shared.isFromAppLink = false
        }
    }
}
