//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public enum FaceLivenessFailureReason
{
    None,
    FaceMouthRegionNotVisible,
    FaceEyeRegionNotVisible,
    ExcessiveImageBlurDetected,
    ExcessiveFaceBrightness,
    FaceWithMaskDetected,
    TimedOut,
    EnvironmentNotSupported,
    FaceTrackingFailed,
    UnexpectedClientError,
    UnexpectedServerError,
    Unexpected,
    CameraPermissionDenied,
    CameraStartupFailure,
    SmileNotPerformed,
    HeadTurnNotPerformed,
    ServerTimedOut,
    InvalidToken,
    NoFaceDetected,
    UserCanceledSession,
    UserCanceledActiveMotion,
    UserCanceledActiveMotionPrompt,
    ClientVersionNotSupported,
    VerifyImageNotProvided,
    SetVerifyImageNotAllowed,
    GenericFailure
}