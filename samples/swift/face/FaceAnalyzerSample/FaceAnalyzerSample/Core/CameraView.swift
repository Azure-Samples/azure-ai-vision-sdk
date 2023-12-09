//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

struct CameraView: View {
    @Binding var backgroundColor: Color?
    @Binding var feedbackMessage: String
    @State private var progress: Float? = nil
    static let previewAreaRatio = 0.12
    static let screenAspectRatio = Double(UIScreen.main.bounds.size.height / UIScreen.main.bounds.size.width)
    static let previewWidthRatio = CGFloat(2 * sqrt(previewAreaRatio * screenAspectRatio / Double.pi))
    
    let onViewDidLoad: (VisionSource) -> Void
    
    var body: some View {
        GeometryReader { metrics in
            VStack {
                HStack {
                    Spacer()
                    VStack(alignment: .leading) {
                        CameraPreviewView(onViewDidLoad: onViewDidLoad)
                            .frame(width: metrics.size.width * CameraView.previewWidthRatio,
                                   height: metrics.size.width * CameraView.previewWidthRatio,
                                   alignment: .center)
                            .mask(Circle())
                            .padding(.top)
                    }
                    Spacer()
                }
                Text(feedbackMessage)
                    .fixedSize(horizontal: false, vertical: false)
                    .frame(height: UIFont.labelFontSize * 6, alignment: .topLeading)
                    .font(.system(size: 24))
                    .lineLimit(nil)
                    .foregroundColor(Color.black)
                    .padding(.top, 25)
                Spacer()
            }
        }.background(backgroundColor)
    }
}

