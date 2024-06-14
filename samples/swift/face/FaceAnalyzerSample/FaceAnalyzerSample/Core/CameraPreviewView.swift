//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AzureAIVisionFace

struct CameraPreviewView: View {
    @EnvironmentObject private var actor: LivenessModel
    @Binding var isCameraPreviewVisible: Bool
    let onViewDidLoad: (VisionSource) -> Void
    
    var body: some View {
            CameraRepresentable { view in
                if let visionSourceOption = VisionSourceOptions.init(deviceOption: VisionSourceDeviceOption.defaultCaptureDevice) {
                    DispatchQueue.main.async {
                        if actor.source == nil,
                           let source = try? VisionSource.init(sourceOptions: visionSourceOption) {
                            actor.source = source
                        }
                        if let source = actor.source {
                            source.getVisionCamera()?.setPreviewView(view)
                            onViewDidLoad(source)
                        }
                    }
                }
            }
            .opacity(isCameraPreviewVisible ? 1 : 0)
    }
}
