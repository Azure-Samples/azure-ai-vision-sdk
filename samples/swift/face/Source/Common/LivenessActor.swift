import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

class LivenessActor
{
    private var faceAnalyzer: FaceAnalyzer? = nil

    let userFeedbackHandler: (String) -> Void
    let screenBackgroundColorHandler: (Color) -> Void
    let resultHandler: (String) -> Void
    let completionHandler: (FaceAnalyzedDetails?) -> Void
    let logHandler: () -> Void
    
    func stopAnalyzer() {
        if let analyzer = self.faceAnalyzer
        {
            try! analyzer.stopAnalyzeOnce()
            self.faceAnalyzer = nil
        }
    }

    init(userFeedbackHandler: @escaping (String) -> Void,
         resultHandler: @escaping (String) -> Void,
         screenBackgroundColorHandler: @escaping (Color) -> Void,
         completionHandler: @escaping (FaceAnalyzedDetails?) -> Void,
         logHandler: @escaping () -> Void) {
        self.resultHandler = resultHandler
        self.userFeedbackHandler = userFeedbackHandler
        self.screenBackgroundColorHandler = screenBackgroundColorHandler
        self.completionHandler = completionHandler
        self.logHandler = logHandler
    }

    func start(usingSource visionSource: VisionSource,
               parameters: SessionData) async
    {
        let methodOptions: FaceAnalysisOptions
        do {
            let createOptions = try FaceAnalyzerCreateOptions()
            methodOptions = try FaceAnalysisOptions()

            var serviceOptions: VisionServiceOptions? = nil
            serviceOptions = try VisionServiceOptions(endpoint: "")
            serviceOptions?.authorizationToken = parameters.token!

            if (parameters.livenessWithVerify) {
                if (parameters.verificationImage!.cgImage != nil) {
                    let verifyImage = parameters.verificationImage!.cgImage!
                    let verificationFrameSource = getSourceFromImage(image: parameters.verificationImage!)
                    let verificationFrame = getFrameFromImage(image: parameters.verificationImage!)
                    try verificationFrameSource.frameWriter?.write(verificationFrame)
                    let verificationVisionSource = try VisionSource(frameSource: verificationFrameSource)
                    try methodOptions.setRecognitionMode(.verifyMatchToFaceIn(singleFaceImage: verificationVisionSource))
                }
                else
                {
                    print("Error: missing verification image!")
                    return
                }
            }

            createOptions.faceAnalyzerMode = FaceAnalyzerMode.trackFacesAcrossImageStream
            methodOptions.faceSelectionMode = FaceSelectionMode.largest
            faceAnalyzer = await try FaceAnalyzer.create(serviceOptions: serviceOptions, input: visionSource, createOptions: createOptions)
        }
        catch {
            self.resultHandler("Error configuring service")
            return
        }

        guard faceAnalyzer != nil else {
            self.resultHandler("Error creating FaceAnalyzer")
            return
        }

        let visionCamera = visionSource.getVisionCamera()
        faceAnalyzer?.addAnalyzedEventHandler {[parameters] (analyzer: FaceAnalyzer, result: FaceAnalyzedResult) in
            print("session analyzed event callback")

            var livenessResultString: String = "Liveness status: "
            let count = result.faces.count
            guard  count != 0  else {
                self.resultHandler("Result has no faces")
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
                        if let resultId = livenessResult?.resultId
                        {
                            parameters.resultId = resultId.uuidString
                        }
                    }
                    else
                    {
                        parameters.resultId = UUID().uuidString
                    }
                }
            }
            
            if (parameters.livenessWithVerify)
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
            self.resultHandler(livenessResultString)
            self.completionHandler(faceAnalyzedDetails)
            print(livenessResultString)
        }

        faceAnalyzer?.addAnalyzingEventHandler { (analyzer: FaceAnalyzer, result: FaceAnalyzingResult) in
            var userNotification = ""
            let count = result.faces.count
            guard  count != 0  else {
                self.resultHandler("Result has no faces")
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
