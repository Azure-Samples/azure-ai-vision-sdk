//
//  FaceAnalyzerSampleAppClipApp.swift
//  FaceAnalyzerSampleAppClip
//
//  Created by MSFACE on 6/3/24.
//

import SwiftUI
import AVFoundation

@main
struct FaceAnalyzerSampleAppClipApp: App {
    @StateObject var sessionData = SessionData()
    @StateObject var pageSelection = PageSelection()
    @StateObject private var errorState = ErrorState()
    @StateObject var revealedPage = PageReveal()
    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(pageSelection)
                .environmentObject(sessionData)
                .environmentObject(errorState)
                .environmentObject(revealedPage)
                .modifier(ErrorAlert(errorState: errorState))
                .modifier(LinkAuthProgressOverlay())
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                    if let url = userActivity.webpageURL {
                        checkCameraAccess(urlToOpen: url)
                    }
                }
                .onOpenURL{url in
                    checkCameraAccess(urlToOpen: url)
                }
        }
    }
    func checkCameraAccess(urlToOpen: URL?) {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)

        switch cameraStatus {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if !granted {
                    errorState.show("Camera Access Denied. Please delete the app clip and try again.")
                }
                else {
                    //camera granted
                    if((urlToOpen) != nil){
                        processUniversalLink(url: urlToOpen!, sessionData: sessionData, pageSelection: pageSelection, errorState: errorState)
                    }
                }
            }
        case .denied, .restricted:
            errorState.show("Camera Access Denied. Please delete the app clip and try again.")
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

