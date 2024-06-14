//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI

struct ClientStartView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @EnvironmentObject var livenessModel: LivenessModel

    @State private var backgroundColor: Color = Color.white

    var body: some View {
        VStack() {
            Spacer()
            Button(action: startClient, label: {
                Text("Start").font(.title)
                    .padding()
            })
                .disabled(sessionData.token == nil || sessionData.token == "")
            Spacer()
        }
    }

    func startClient() {
        livenessModel.reset()
        withAnimation {
            pageSelection.current = .liveness
        }
    }
}
