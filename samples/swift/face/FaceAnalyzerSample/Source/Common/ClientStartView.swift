//
//  ResultView.swift
//  MobileFaceRecognition
//
//  Created by MSFACE on 10/6/23.
//

import Foundation
import SwiftUI

struct ClientStartView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @Environment(\.presentationMode) var presentationMode

    @State private var backgroundColor: Color = Color.white

    var body: some View {
        VStack() {
            Spacer()
            Button(action: startClient, label: {
                Text("Start").font(.title)
                    .padding()
            })
            .disabled(sessionData.token == "")
            Spacer()
        }
    }

    func startClient() {
        withAnimation {
            pageSelection.current = .liveness
        }
    }
}
