//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

import AzureAIVisionFaceUI

@objc public protocol FaceLivenessDetectorWrapperDelegate {
    @objc func resultHasChanged(result: LivenessDetectionResultWrapper)
}
