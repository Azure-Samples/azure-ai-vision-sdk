//
//  Localization.swift
//  FaceAnalyzerDemo
//
//  Created by MSFACE on 11/8/23.
//

import Foundation

struct LocalizationStrings {
    static let recognitionStatusNotComputed = NSLocalizedString("Not computed", comment: "")
    static let recognitionStatusFailed = NSLocalizedString("Failed", comment: "")
    static let recognitionStatusNotRecognized = NSLocalizedString("Not recognized", comment: "")
    static let recognitionStatusRecognized = NSLocalizedString("Recognized", comment: "")
    static let recognitionStatusUnknown = NSLocalizedString("Unknown", comment: "")

    static let recognitionFailureExcessiveFaceBrightness = NSLocalizedString("ExcessiveFaceBrightness \n", comment: "")
    static let recognitionFailureExcessiveImageBlurDetected = NSLocalizedString("ExcessiveImageBlurDetected \n", comment: "")
    static let recognitionFailureFaceEyeRegionNotVisible = NSLocalizedString("FaceEyeRegionNotVisible \n", comment: "")
    static let recognitionFailureFaceNotFrontal = NSLocalizedString("FaceNotFrontal \n", comment: "")
    static let recognitionFailureNone = NSLocalizedString("None", comment: "")
    static let recognitionFailureFaceNotFound = NSLocalizedString("FaceNotFound", comment: "")
    static let recognitionFailureMultipleFaceFound = NSLocalizedString("MultipleFaceFound", comment: "")
    static let recognitionFailureContentDecodingError = NSLocalizedString("ContentDecodingError", comment: "")
    static let recognitionFailureImageSizeIsTooLarge = NSLocalizedString("ImageSizeIsTooLarge", comment: "")
    static let recognitionFailureImageSizeIsTooSmall = NSLocalizedString("ImageSizeIsTooSmall", comment: "")
    static let recognitionFailureGenericFailure = NSLocalizedString("GenericFailure \n", comment: "")

    static let livenessStatusNotComputed = NSLocalizedString("NotComputed", comment: "")
    static let livenessStatusFailed = NSLocalizedString("Failed", comment: "")
    static let livenessStatusLive = NSLocalizedString("Live", comment: "")
    static let livenessStatusSpoof = NSLocalizedString("Spoof", comment: "")
    static let livenessStatusUnknown = NSLocalizedString("Unknown", comment: "")

    static let livenessFailureNone = NSLocalizedString("None", comment: "")
    static let livenessFailureFaceMouthRegionNotVisible = NSLocalizedString("FaceMouthRegionNotVisible", comment: "")
    static let livenessFailureFaceEyeRegionNotVisible = NSLocalizedString("FaceEyeRegionNotVisible", comment: "")
    static let livenessFailureExcessiveImageBlurDetected = NSLocalizedString("ExcessiveImageBlurDetected", comment: "")
    static let livenessFailureExcessiveFaceBrightness = NSLocalizedString("ExcessiveFaceBrightness", comment: "")
    static let livenessFailureFaceWithMaskDetected = NSLocalizedString("FaceWithMaskDetected", comment: "")
    static let livenessFailureActionNotPerformed = NSLocalizedString("ActionNotPerformed", comment: "")
    static let livenessFailureTimedOut = NSLocalizedString("TimedOut", comment: "")
    static let livenessFailureEnvironmentNotSupported = NSLocalizedString("EnvironmentNotSupported", comment: "")
    static let livenessFailureUnknown = NSLocalizedString("Unknown", comment: "")

    static let faceActionNone = NSLocalizedString("None", comment: "")
    static let faceActionBrightenDisplay = NSLocalizedString("Brighten display", comment: "")
    static let faceActionDarkenDisplay = NSLocalizedString("Darken display", comment: "")

    static let faceFeedbackFaceNotCentered = NSLocalizedString("Look at camera.", comment: "")
    static let faceFeedbackLookAtCamera = NSLocalizedString("Look at camera.", comment: "")
    static let faceFeedbackMoveBack = NSLocalizedString("Too close! Move farther away.", comment: "")
    static let faceFeedbackMoveCloser = NSLocalizedString("Too far away! Move in closer.", comment: "")
    static let faceFeedbackTooMuchMovement = NSLocalizedString("Too much movement.", comment: "")
    static let faceFeedbackAttentionNotNeeded = NSLocalizedString("Done, finishing up...", comment: "")
    static let faceFeedbackHoldStill = NSLocalizedString("Hold still.", comment: "")

    static let faceWarningFaceTooBright = NSLocalizedString("Face too bright", comment: "")
    static let faceWarningFaceTooDark = NSLocalizedString("Face too dark", comment: "")
    static let faceWarningTooBlurry = NSLocalizedString("Face too blurry", comment: "")
    static let faceWarningLowFidelityFaceRegion = NSLocalizedString("Face region low fidelity", comment: "")
    static let faceWarningNone = NSLocalizedString("None", comment: "")

    static let faceTrackingStateNone = NSLocalizedString("No face", comment: "")
    static let faceTrackingStateNew = NSLocalizedString("New face", comment: "")
    static let faceTrackingStateTracked = NSLocalizedString("Face tracked", comment: "")
    static let faceTrackingStateLost = NSLocalizedString("Face lost", comment: "")
    static let faceTrackingStateUnknown = NSLocalizedString("Unknown", comment: "")
}
