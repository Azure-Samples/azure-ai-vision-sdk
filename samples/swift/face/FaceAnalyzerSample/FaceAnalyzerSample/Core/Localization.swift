//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

struct LocalizationStrings {
    static let recognitionStatusNotComputed = NSLocalizedString("Not computed", comment: "")
    static let recognitionStatusFailed = NSLocalizedString("Failed", comment: "")
    static let recognitionStatusNotRecognized = NSLocalizedString("Not recognized", comment: "")
    static let recognitionStatusRecognized = NSLocalizedString("Recognized", comment: "")
    static let recognitionStatusUnknown = NSLocalizedString("Unknown", comment: "")

    static let recognitionFailureExcessiveFaceBrightness = NSLocalizedString("ExcessiveFaceBrightness", comment: "")
    static let recognitionFailureExcessiveImageBlurDetected = NSLocalizedString("ExcessiveImageBlurDetected", comment: "")
    static let recognitionFailureFaceEyeRegionNotVisible = NSLocalizedString("FaceEyeRegionNotVisible", comment: "")
    static let recognitionFailureFaceNotFrontal = NSLocalizedString("FaceNotFrontal", comment: "")
    static let recognitionFailureNone = NSLocalizedString("None", comment: "")
    static let recognitionFailureFaceNotFound = NSLocalizedString("FaceNotFound", comment: "")
    static let recognitionFailureMultipleFaceFound = NSLocalizedString("MultipleFaceFound", comment: "")
    static let recognitionFailureContentDecodingError = NSLocalizedString("ContentDecodingError", comment: "")
    static let recognitionFailureImageSizeIsTooLarge = NSLocalizedString("ImageSizeIsTooLarge", comment: "")
    static let recognitionFailureImageSizeIsTooSmall = NSLocalizedString("ImageSizeIsTooSmall", comment: "")
    static let recognitionFailureGenericFailure = NSLocalizedString("GenericFailure", comment: "")

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

    static let faceFeedbackFaceNotCentered = NSLocalizedString("Look at camera.", comment: "")
    static let faceFeedbackLookAtCamera = NSLocalizedString("Look at camera.", comment: "")
    static let faceFeedbackMoveBack = NSLocalizedString("Too close! Move farther away.", comment: "")
    static let faceFeedbackMoveCloser = NSLocalizedString("Too far away! Move in closer.", comment: "")
    static let faceFeedbackTooMuchMovement = NSLocalizedString("Too much movement.", comment: "")
    static let faceFeedbackAttentionNotNeeded = NSLocalizedString("Done, finishing up...", comment: "")
    static let faceFeedbackHoldStill = NSLocalizedString("Hold still.", comment: "")
}
