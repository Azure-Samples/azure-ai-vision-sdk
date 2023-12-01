//
//  FaceAnalyzerSampleApp.swift
//  FaceAnalyzerSample
//
//  Created by MSFACE on 11/20/23.
//

import SwiftUI

@main
struct FaceAnalyzerSample: App {
    @StateObject var pageSelection = PageSelection()
    @StateObject var sessionData = SessionData()

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(pageSelection)
                .environmentObject(sessionData)
        }
    }
}

struct Previews_FaceAnalyzerSample_Previews: PreviewProvider {
    static var previews: some View {
        Text("Hello, World!")
    }
}
