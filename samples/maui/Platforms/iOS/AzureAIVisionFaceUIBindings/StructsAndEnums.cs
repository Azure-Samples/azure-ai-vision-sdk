//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using ObjCRuntime;

namespace AzureAIVisionFaceUI
{
    [Native]
    public enum AZFaceRecognitionFailureReason : ulong
    {
        None,
        GenericFailure,
        FaceNotFrontal,
        FaceEyeRegionNotVisible,
        ExcessiveFaceBrightness,
        ExcessiveImageBlurDetected,
        FaceNotFound,
        MultipleFaceFound,
        ContentDecodingError,
        ImageSizeIsTooLarge,
        ImageSizeIsTooSmall,
        UnsupportedMediaType,
        FaceMouthRegionNotVisible,
        FaceWithMaskDetected
    }

    [Native]
    public enum AZFaceLivenessFailureReason : ulong
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
}
