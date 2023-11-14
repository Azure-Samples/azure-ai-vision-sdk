//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

struct LivenessView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @Environment(\.presentationMode) var presentationMode

    @State private var actor: LivenessActor? = nil
    // we can add localization
    @State private var feedbackMessage: String = "Hold still."
    @State private var httpRequest: String = ""
    @State private var backgroundColor: Color? = Color.white
    @State var resultAvailable: Bool {
        didSet {
            if (resultAvailable) {
                actionDidComplete()
            }
        }
    }

    var body: some View {
        ZStack(alignment: .center) {
            CameraView(
                backgroundColor: $backgroundColor,
                feedbackMessage: $feedbackMessage,
                httpRequest: $httpRequest) { visionSource in
                    let actor = self.actor ?? LivenessActor.init(
                        userFeedbackHandler: { feedback in
                            feedbackMessage = feedback // original input
                        },
                        resultHandler: {result in
                            sessionData.resultMessage = result
                            resultAvailable = true
                        },
                        screenBackgroundColorHandler: { color in
                            backgroundColor = color
                        },
                        completionHandler: { faceAnalyzedDetails in
                            // Not needed Here
                        },
                        logHandler: {
                            // for customized logging purpose
                        })
                    self.actor = actor
                    Task {
                        await actor.start(usingSource: visionSource, parameters: sessionData)
                    }
                }
        }
    }

    func actionDidComplete() {
        self.actor?.stopAnalyzer()
        DispatchQueue.main.async {
            withAnimation {
                pageSelection.current = .result
            }
        }
    }

}

