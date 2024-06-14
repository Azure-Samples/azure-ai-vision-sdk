//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

class LivenessActor
{
    private var resultId: String = ""
    private var resultDigest: String = ""

    private let withVerification: Bool
    private let faceAnalyzer: FaceAnalyzer
    private let userFeedbackHandler: (String) -> Void
    private let screenBackgroundColorHandler: (Color) -> Void
    private let resultHandler: (String, String, String) -> Void
    private let detailsHandler: (FaceAnalyzedDetails?) -> Void
    private let logHandler: () -> Void
    private let stopCameraHandler: () -> Void

    func stopAnalyzer() {
        try! self.faceAnalyzer.stopAnalyzeOnce()
    }

    init(faceAnalyzer: FaceAnalyzer,
         withVerification: Bool,
         userFeedbackHandler: @escaping (String) -> Void,
         resultHandler: @escaping (String, String, String) -> Void,
         screenBackgroundColorHandler: @escaping (Color) -> Void,
         detailsHandler: @escaping (FaceAnalyzedDetails?) -> Void,
         logHandler: @escaping () -> Void,
         stopCameraHandler: @escaping () -> Void) {
        self.faceAnalyzer = faceAnalyzer
        self.withVerification = withVerification
        self.resultHandler = resultHandler
        self.userFeedbackHandler = userFeedbackHandler
        self.screenBackgroundColorHandler = screenBackgroundColorHandler
        self.detailsHandler = detailsHandler
        self.logHandler = logHandler
        self.stopCameraHandler = stopCameraHandler
    }
    
    static func createFaceAnalyzer(source: VisionSource,
                                   sessionAuthorizationToken: String) async throws -> FaceAnalyzer? {
        guard let createOptions = try? FaceAnalyzerCreateOptions() else {
            return nil
        }

        var serviceOptions = try VisionServiceOptions()
        serviceOptions.authorizationToken = sessionAuthorizationToken

        createOptions.faceAnalyzerMode = FaceAnalyzerMode.trackFacesAcrossImageStream
        return try await FaceAnalyzer.create(serviceOptions: serviceOptions, input: source, createOptions: createOptions)
    }

    func start() async {

        self.faceAnalyzer.addAnalyzedEventHandler {[] (analyzer: FaceAnalyzer, result: FaceAnalyzedResult) in
            print("session analyzed event callback")

            // this result is used for sample App demo purpose only, you should handle the liveness results in your own way
            var livenessResultString: String = "Liveness status: "
            let count = result.faces.count
            guard  count != 0  else {
                self.resultHandler("Result has no faces", self.resultId, self.resultDigest)
                return
            }
            let face = result.faces[result.faces.startIndex]
            let livenessResult = face.livenessResult
            let faceAnalyzedDetails = result.faceAnalyzedDetails;

            if (livenessResult != nil)
            {
                if let status = livenessResult?.status
                {
                    livenessResultString += LivenessStatusToString(status: status) + "\n"
                    if let failure = livenessResult?.failureReason
                    {
                        if (status == FaceLivenessStatus.failed)
                        {
                            livenessResultString += "Liveness Failure Reason: "
                            livenessResultString += LivenessFailureReasonToString(reason: failure) + "\n"
                        }
                    }
                    if status != FaceLivenessStatus.notComputed
                    {
                        if let sessionResultId = livenessResult?.resultId
                        {
                            self.resultId = sessionResultId.uuidString
                        }
                        if let sessionResultDigest = faceAnalyzedDetails?.digest
                        {
                            self.resultDigest = sessionResultDigest
                            print(self.resultDigest)
                            print(self.resultId)
                        }
                    }
                }
            }
            
            if (self.withVerification)
            {
                let recoResult = face.recognitionResult
                if (recoResult != nil)
                {
                    if let status = recoResult?.recognitionStatus
                    {
                        livenessResultString += "Verify Result: " + RecognitionStatusToString(status: status) + "\n"
                        livenessResultString += "Verify Score: " + String(recoResult?.confidence ?? 0) + "\n"
                        if let failure = recoResult?.failureReason
                        {
                            if (status == FaceRecognitionStatus.notRecognized)
                            {
                                livenessResultString += "Verify Failure Reason: \n"
                                livenessResultString += RecognitionFailureToString(reason: failure) + "\n"
                            }
                        }
                    }
                }
            }

            self.userFeedbackHandler("")
            self.resultHandler(livenessResultString, self.resultId, self.resultDigest)
            self.detailsHandler(faceAnalyzedDetails)

            print(livenessResultString)
        }

        self.faceAnalyzer.addAnalyzingEventHandler { (analyzer: FaceAnalyzer, result: FaceAnalyzingResult) in
            var userNotification = ""
            let count = result.faces.count
            guard  count != 0  else {
                self.resultHandler("Result has no faces", self.resultId, self.resultDigest)
                return
            }
            let face = result.faces[result.faces.startIndex]

            if let action = face.actionRequired?.action
            {
                //userNotification += FaceActionToString(action: action) + "\n"
                if(FaceActionRequiredFromApplication.brightenDisplay == action)
                {
                    self.screenBackgroundColorHandler(Color.white)
                }
                if(FaceActionRequiredFromApplication.darkenDisplay == action)
                {
                    self.screenBackgroundColorHandler(Color.black)
                }
                if(FaceActionRequiredFromApplication.stopCamera == action)
                {
                    self.stopCameraHandler()
                }

                face.actionRequired?.complete()
            }
            userNotification += FaceFeedbackToString(feedback: face.feedbackForFace) + "\n"

            self.userFeedbackHandler(String(userNotification))
        }

        self.faceAnalyzer.addSessionStartedEventHandler { analyzer, evt in
            print("session started event callback")
        }

        self.faceAnalyzer.addSessionStoppedEventHandler {[weak self] FaceAnalyzer, session, evt in
            print("session stopped event callback")
            self!.userFeedbackHandler("Done, finishing up...")
        }

        let methodOptions: FaceAnalysisOptions
        do {
            methodOptions = try FaceAnalysisOptions()
            methodOptions.faceSelectionMode = FaceSelectionMode.largest
        } catch {
            self.resultHandler("Error configuring analysis", self.resultId, self.resultDigest)
            return
        }

        self.faceAnalyzer.analyzeOnce(using: methodOptions, completionHandler: { (result, error) in
            print("analyzeOnce completion handler")
        })

        // for customized logging usage
        self.logHandler()
    }
}
