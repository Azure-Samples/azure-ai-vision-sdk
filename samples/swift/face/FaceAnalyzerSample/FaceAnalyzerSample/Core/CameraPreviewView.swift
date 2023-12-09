//
// Copyright (c) Microsoft. All rights reserved.
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
