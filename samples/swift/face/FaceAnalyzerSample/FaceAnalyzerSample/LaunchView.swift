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

    @State var monitor: NWPathMonitor? = nil
    @State private var isCameraAccessDeniedAlertPresented = false

    var body: some View {
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
        withAnimation {
            pageSelection.current = .clientStart
            do {
                if let auth = try obtainToken(usingEndpoint: sessionData.endpoint,
                                          key: sessionData.key,
                                          withVerify: false,
                                          livenessOperationMode: sessionData.livenessMode.livenessOperationMode) {
                    sessionData.token = auth.token
                    sessionData.sessionId = auth.id
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
