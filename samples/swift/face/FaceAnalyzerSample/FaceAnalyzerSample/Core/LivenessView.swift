//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

struct LivenessView: View {

    @State private var actor: LivenessActor? = nil
    // localization can be applied to feedbackMessage
    @State private var feedbackMessage: String = "Hold still."
    @State private var resultMessage: String = ""
    @State private var resultId: String = ""
    @State private var resultDigest: String = ""
    @State private var backgroundColor: Color? = Color.white
    @State private var isCameraPreviewVisible: Bool = true
    var logHandler: (() -> Void) = {}

    // required authorization token to initialize and authorize the client, you may consider to create token in your backend directly
    let sessionAuthorizationToken: String
    // boolean indicates whether liveness detection is run with verification or not
    let withVerification: Bool
    // the completion handler used to handle detection results and help on UI switch
    let completionHandler: (String, String, String) -> Void
    // the details handler to get the digest, which can be used to validate the integrity of the transport
    let detailsHandler: (FaceAnalyzedDetails?) -> Void

    init(sessionAuthorizationToken: String,
         withVerification: Bool = false,
         completionHandler: @escaping (String, String, String)->Void = {_,_,_ in },
         detailsHandler: @escaping (FaceAnalyzedDetails?)->Void = {_ in }) {
        self.sessionAuthorizationToken = sessionAuthorizationToken
        self.withVerification = withVerification
        self.completionHandler = completionHandler
        self.detailsHandler = detailsHandler
    }

    var body: some View {
        ZStack(alignment: .center) {
            CameraView(
                backgroundColor: $backgroundColor,
                feedbackMessage: $feedbackMessage,
                isCameraPreviewVisible: $isCameraPreviewVisible) { visionSource in
                    let actor = self.actor ?? LivenessActor.init(
                        userFeedbackHandler: { feedback in
                            self.feedbackMessage = feedback
                        },
                        resultHandler: {result, resultId, resultDigest in
                            // This is just for demo purpose
                            // You should handle the liveness result in your own way
                            self.feedbackMessage = result
                            self.resultMessage = result
                            self.resultId = resultId
                            self.resultDigest = resultDigest
                            self.actionDidComplete()
                        },
                        screenBackgroundColorHandler: { color in
                            self.backgroundColor = color
                        },
                        detailsHandler: { faceAnalyzedDetails in
                            // Not necessary, but you can get the digest details here
                            self.detailsHandler(faceAnalyzedDetails)
                        },
                        logHandler: {
                            self.logHandler()
                        },
                        stopCameraHandler: {
                            self.isCameraPreviewVisible = false
                        },
                        withVerification: self.withVerification)
                    self.actor = actor
                    Task {
                        await actor.start(usingSource: visionSource, sessionAuthorizationToken: self.sessionAuthorizationToken)
                    }
                }
        }
    }

    func actionDidComplete() {
        self.actor?.stopAnalyzer()
        self.completionHandler(resultMessage, resultId, resultDigest)
    }

}

