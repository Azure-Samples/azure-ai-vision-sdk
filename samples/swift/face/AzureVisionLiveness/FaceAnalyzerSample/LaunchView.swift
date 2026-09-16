//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import Network

struct LaunchView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @EnvironmentObject var errorState: ErrorState
    @EnvironmentObject var revealedPage: PageReveal
    @State var monitor: NWPathMonitor? = nil
    @State private var isCameraAccessDeniedAlertPresented = false
    // Number of taps required to reveal the button
    private let requiredTaps = 10
    @State private var tapCount = 0
    @State private var showFeedback = false
    
    // Animations
    @State private var rotateBG: Bool = false
    @State private var breathe: Bool = false
    @State private var shimmerPhase: CGFloat = -1.0
    
    private let learnMoreURL = URL(string: "https://www.microsoft.com/privacy/data-privacy-notice")!
    private func openLearnMore() {
            #if canImport(UIKit)
            UIApplication.shared.open(learnMoreURL, options: [:], completionHandler: nil)
            #endif
        }
    var body: some View {
        ZStack {
            GeometryReader { geo in
                ZStack {
                    Circle()
                        .fill(LinearGradient(
                            gradient: Gradient(colors: [Color.purple.opacity(0.7), Color.blue.opacity(0.5)]),
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: max(geo.size.width, geo.size.height) * 0.9)
                        .blur(radius: 60)
                        .offset(x: -geo.size.width * 0.2, y: -geo.size.height * 0.25)

                    Circle()
                        .fill(LinearGradient(
                            gradient: Gradient(colors: [
                                Color(red: 0.0, green: 1.0, blue: 1.0).opacity(0.6),   // cyan (iOS14-safe)
                                Color(red: 0.29, green: 0.0, blue: 0.51).opacity(0.6)  // indigo (iOS14-safe)
                            ]),
                            startPoint: .topTrailing,
                            endPoint: .bottomLeading))
                        .frame(width: max(geo.size.width, geo.size.height) * 0.9)
                        .blur(radius: 60)
                        .offset(x: geo.size.width * 0.25, y: geo.size.height * 0.2)
                }
                .rotationEffect(.degrees(rotateBG ? 360 : 0))
            }
            .edgesIgnoringSafeArea(.all) // iOS 14-safe
            .contentShape(Rectangle())
            .onTapGesture { // Stop counting once revealed
                guard !revealedPage.revealed else { return }

                tapCount += 1

                // Subtle haptic on each tap (optional)
                let generator = UIImpactFeedbackGenerator(style: .light)
                generator.impactOccurred()

                if tapCount >= requiredTaps {
                    revealSecret()
                }
            }
            // Foreground content
            VStack(spacing: 20) {
                            Image(systemName: "person.crop.circle.fill.badge.checkmark")
                                .font(.system(size: 64, weight: .semibold))
                                .foregroundColor(Color.white.opacity(0.95))
                                .scaleEffect(breathe ? 1.05 : 0.92)
                                .shadow(color: Color.white.opacity(0.35), radius: 12, x: 0, y: 8)
                                .animation(Animation.easeInOut(duration: 1.6).repeatForever(autoreverses: true))

                            // Title
                            Text("We care about your privacy and security")
                                .font(.system(.headline, design: .rounded).weight(.semibold))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)

                            // Body copy
                            Text("Microsoft's facial recognition software will check your selfie to generate a liveness score. Your selfie will not be stored after this analysis.")
                                .font(.system(.footnote, design: .rounded))
                                .foregroundColor(Color.white.opacity(0.9))
                                .multilineTextAlignment(.center)

                            // Learn more (iOS 14-safe tappable text)
                            Button(action: openLearnMore) {
                                Text("Learn more")
                                    .underline()
                                    .font(.system(.footnote, design: .rounded).weight(.medium))
                                    .foregroundColor(.white)
                                    .accessibilityLabel("Learn more about privacy and data usage")
                            }
                            .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.horizontal, 28)
                    .onAppear {
                        withAnimation(
                            Animation.easeInOut(duration: 1.6)
                                .repeatForever(autoreverses: true)
                        ) {
                            breathe.toggle()
                        }
                    }
            
            

            // Hidden button (reveals after requiredTaps)
            if revealedPage.revealed {
                Form {
                    Section(header: Text("Select analyzer mode to begin")) {
                        Button(action: livenessClicked) {
                            Text("Liveness")
                                .font(.title)
                                .padding()
                                .disabled(!sessionData.settingsConfigured)
                        }.accessibilityIdentifier("livenessButton")
                        Button(action: livenessWithVerifyClicked) {
                            Text("LivenessWithVerify")
                                .font(.title)
                                .padding()
                                .disabled(!sessionData.settingsConfigured)
                        }.accessibilityIdentifier("livenessWithVerifyButton")
                        Section {
                            Button(action: settingClicked) {
                                Text("Settings")
                                    .font(.title)
                                    .padding()
                            }.accessibilityIdentifier( "settingsButton")
                        }
                    }
                    // display version
                    if let infoDictionary = Bundle.main.infoDictionary,
                    let version = infoDictionary ["CFBundleVersion"] as? String {
                        Section(header: Text("App v\(version)")) { }
                    }
                }.onAppear {
                    // camera permission check
                    checkCameraAccess()

                    // network notification
                    if self.monitor == nil {
                        let monitor = NWPathMonitor()
                        monitor.pathUpdateHandler = { path in
                            if path.status != .satisfied {
                                // Not connected
                                DispatchQueue.main.async {
                                    sessionData.isNetworkAvailable = false
                                }
                            }
                            else if path.usesInterfaceType(.cellular) || path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet) {
                                DispatchQueue.main.async {
                                    sessionData.isNetworkAvailable = true
                                }
                            }
                        }
                        monitor.start(queue: DispatchQueue.global(qos: .background))
                        self.monitor = monitor
                    }
                    loadDataFromFile(sessionData: sessionData)
                }
                .alert(isPresented: $isCameraAccessDeniedAlertPresented) {
                    Alert(
                        title: Text("Camera Access Denied"),
                        message: Text("Please go to Settings and enable camera access for this app."),
                        dismissButton: .default(Text("OK"))
                    )
                }
            }
        }
        .animation(.easeInOut(duration: 0.35), value: revealedPage.revealed)
        .accessibilityAddTraits(.isButton)
    }

    private func revealSecret() {
        withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) {
            revealedPage.revealed = true
            showFeedback = true
        }

        // stronger haptic to let user know something special happened
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        // remove the tiny pulse after a short delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            withAnimation(.easeOut(duration: 0.2)) {
                showFeedback = false
            }
        }
    }
    

    func checkCameraAccess() {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)

        switch cameraStatus {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    isCameraAccessDeniedAlertPresented.toggle()
                }
            }
        case .denied, .restricted:
            isCameraAccessDeniedAlertPresented.toggle()
        case .authorized:
            break
        default:
            break
        }
    }

    func livenessClicked() {
        if !sessionData.settingsConfigured { return }
        sessionData.livenessWithVerify = false
        sessionData.token = nil
        sessionData.sessionId = nil
        AppLinkState.shared.isFromAppLink = false
        withAnimation {
            pageSelection.current = .clientStart
            do {
                if let auth = try obtainToken(usingEndpoint: sessionData.endpoint,
                                          key: sessionData.key,
                                          withVerify: false,
                                          livenessOperationMode: sessionData.livenessMode.livenessOperationMode) {
                    sessionData.token = auth.token
                    sessionData.sessionId = auth.id
                    sessionData.deviceCorrelationIdInClient = nil
                }
            } catch {
                errorState.show(error.localizedDescription) {
                    // Optional: on dismiss, go back to launch page
                    pageSelection.current = .launch
                }
            }
        }
    }

    func livenessWithVerifyClicked() {
        if !sessionData.settingsConfigured { return }
        sessionData.livenessWithVerify = true
        withAnimation {
            pageSelection.current = .imageSelection
        }
    }

    func settingClicked() {
        withAnimation {
            pageSelection.current = .settings
        }
    }
}
