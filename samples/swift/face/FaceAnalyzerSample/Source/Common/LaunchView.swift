//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import Foundation
import SwiftUI
import AVFoundation
import Network

struct LaunchView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    
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
                }
                Button(action: livenessWithVerifyClicked) {
                    Text("LivenessWithVerify")
                        .font(.title)
                        .padding()
                        .disabled(!sessionData.settingsConfigured)
                }
                Section {
                    Button(action: settingClicked) {
                        Text("Settings")
                            .font(.title)
                            .padding()
                    }
                }
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
        withAnimation {
            pageSelection.current = .clientStart
            sessionData.token = obtainToken(usingEndpoint: sessionData.endpoint, key: sessionData.key, withVerify: false)
        }
    }
    func livenessWithVerifyClicked() {
        if !sessionData.settingsConfigured { return }
        sessionData.livenessWithVerify = true
        withAnimation {
            pageSelection.current = .imageSelection
            sessionData.token = obtainToken(usingEndpoint: sessionData.endpoint, key: sessionData.key, withVerify: true)
        }
    }
    func settingClicked() {
        withAnimation {
            pageSelection.current = .settings
        }
    }
}
