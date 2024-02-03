//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

class LivenessActor
{
    private var faceAnalyzer: FaceAnalyzer? = nil
    private var withVerification: Bool = false
    private var referenceImage: UIImage? = nil
    private var resultId: String = ""
    private var resultDigest: String = ""

    let userFeedbackHandler: (String) -> Void
    let screenBackgroundColorHandler: (Color) -> Void
    let resultHandler: (String, String, String) -> Void
    let detailsHandler: (FaceAnalyzedDetails?) -> Void
    let logHandler: () -> Void
    let stopCameraHandler: () -> Void

    func stopAnalyzer() {
        if let analyzer = self.faceAnalyzer
        {
            try! analyzer.stopAnalyzeOnce()
            self.faceAnalyzer = nil
        }
    }

    init(userFeedbackHandler: @escaping (String) -> Void,
         resultHandler: @escaping (String, String, String) -> Void,
         screenBackgroundColorHandler: @escaping (Color) -> Void,
         detailsHandler: @escaping (FaceAnalyzedDetails?) -> Void,
         logHandler: @escaping () -> Void,
         stopCameraHandler: @escaping () -> Void,
         withVerification: Bool,
         referenceImage: UIImage?) {
        self.resultHandler = resultHandler
        self.userFeedbackHandler = userFeedbackHandler
        self.screenBackgroundColorHandler = screenBackgroundColorHandler
        self.detailsHandler = detailsHandler
        self.logHandler = logHandler
        self.withVerification = withVerification
        self.referenceImage = referenceImage
        self.stopCameraHandler = stopCameraHandler
    }

    func start(usingSource visionSource: VisionSource,
               sessionAuthorizationToken: String) async
    {
        let methodOptions: FaceAnalysisOptions
        do {
            let createOptions = try FaceAnalyzerCreateOptions()
            methodOptions = try FaceAnalysisOptions()

            var serviceOptions: VisionServiceOptions? = nil
            serviceOptions = try VisionServiceOptions(endpoint: "")
            serviceOptions?.authorizationToken = sessionAuthorizationToken

            if (withVerification) {
                if (referenceImage!.cgImage != nil) {
                    let verifyImage = referenceImage!.cgImage!
                    let verificationFrameSource = getSourceFromImage(image: referenceImage!)
                    let verificationFrame = getFrameFromImage(image: referenceImage!)
                    try verificationFrameSource.frameWriter?.write(verificationFrame)
                    let verificationVisionSource = try VisionSource(frameSource: verificationFrameSource)
                    try methodOptions.setRecognitionMode(.verifyMatchToFaceIn(singleFaceImage: verificationVisionSource))
                }
            }

            createOptions.faceAnalyzerMode = FaceAnalyzerMode.trackFacesAcrossImageStream
            methodOptions.faceSelectionMode = FaceSelectionMode.largest
            faceAnalyzer = try await FaceAnalyzer.create(serviceOptions: serviceOptions, input: visionSource, createOptions: createOptions)
        }
        catch {
            self.resultHandler("Error configuring service", self.resultId, self.resultDigest)
            return
        }

        guard faceAnalyzer != nil else {
            self.resultHandler("Error creating FaceAnalyzer", self.resultId, self.resultDigest)
            return
        }

        let visionCamera = visionSource.getVisionCamera()
        faceAnalyzer?.addAnalyzedEventHandler {[] (analyzer: FaceAnalyzer, result: FaceAnalyzedResult) in
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

        faceAnalyzer?.addAnalyzingEventHandler { (analyzer: FaceAnalyzer, result: FaceAnalyzingResult) in
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

        faceAnalyzer?.addSessionStartedEventHandler { analyzer, evt in
            print("session started event callback")
        }

        faceAnalyzer?.addSessionStoppedEventHandler {[weak self] FaceAnalyzer, session, evt in
            print("session stopped event callback")
            self!.userFeedbackHandler("Done, finishing up...")
        }

        faceAnalyzer?.analyzeOnce(using: methodOptions,completionHandler: { (result, error) in
            print("analyzeOnce completion handler")
        })

        // for customized logging usage
        self.logHandler()
    }
}
