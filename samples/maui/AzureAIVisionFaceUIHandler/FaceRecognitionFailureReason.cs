//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

namespace AzureAIVisionFaceUIHandler;

public enum FaceRecognitionFailureReason
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