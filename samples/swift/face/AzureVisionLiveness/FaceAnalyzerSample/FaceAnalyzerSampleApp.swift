//
//  FaceAnalyzerSampleApp.swift
//  FaceAnalyzerSample
//
//  Created by MSFACE on 11/20/23.
//

import SwiftUI
import AVFoundation


@main
struct FaceAnalyzerSample: App {
    @StateObject var pageSelection = PageSelection()
    @StateObject var sessionData = SessionData()
    @StateObject private var errorState = ErrorState()
    @StateObject var revealedPage = PageReveal()
    @State private var isCameraAccessDeniedAlertPresented = false
    
    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(pageSelection)
                .environmentObject(sessionData)
                .environmentObject(errorState)
                .environmentObject(revealedPage)
                .modifier(ErrorAlert(errorState: errorState))
                .modifier(LinkAuthProgressOverlay())
                .onOpenURL{url in
                    checkCameraAccess(urlToOpen: url)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                    if let url = userActivity.webpageURL {
                        checkCameraAccess(urlToOpen: url)
                    }
                }
        }
    }
    func checkCameraAccess(urlToOpen: URL?) {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)

        switch cameraStatus {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    isCameraAccessDeniedAlertPresented.toggle()
                }
                else {
                    //camera granted
                    if((urlToOpen) != nil){
                        processUniversalLink(url: urlToOpen!, sessionData: sessionData, pageSelection: pageSelection, errorState: errorState)
                    }
                }
            }
        case .denied, .restricted:
            isCameraAccessDeniedAlertPresented.toggle()
        case .authorized:
            // camera granted
            if((urlToOpen) != nil){
                processUniversalLink(url: urlToOpen!, sessionData: sessionData, pageSelection: pageSelection, errorState: errorState)
            }
            break
        default:
            break
        }
    }
}

struct Previews_FaceAnalyzerSample_Previews: PreviewProvider {
    static var previews: some View {
        Text("Hello, World!")
    }
}
