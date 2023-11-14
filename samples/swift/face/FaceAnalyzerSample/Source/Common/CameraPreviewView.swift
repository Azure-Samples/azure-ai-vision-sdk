//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import Foundation
import SwiftUI
import AzureAIVisionFace

struct CameraPreviewView: View {
    let onViewDidLoad: (VisionSource) -> Void
    
    var body: some View {
        CameraRepresentable { view in
            if let visionSourceOption = VisionSourceOptions.init(deviceOption: VisionSourceDeviceOption.defaultCaptureDevice, preview: view) {
                DispatchQueue.main.async {
                    onViewDidLoad(try! VisionSource.init(sourceOptions: visionSourceOption))
                }
            }
        }
    }
}
