//
//  FaceAnalyzerDemoApp.swift
//  FaceAnalyzerDemo
//
//  Created by MSFACE on 11/7/23.
//

import SwiftUI

@main
struct FaceAnalyzerDemo: App {
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

struct Previews_FaceAnalyzerDemo_Previews: PreviewProvider {
    static var previews: some View {
        /*@START_MENU_TOKEN@*/Text("Hello, World!")/*@END_MENU_TOKEN@*/
    }
}
