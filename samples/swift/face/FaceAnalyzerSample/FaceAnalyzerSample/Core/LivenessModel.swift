//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import AzureAIVisionCore
import AzureAIVisionFace

class LivenessModel: ObservableObject {
    @Published var source: VisionSource? = nil
    @Published var analyzer: FaceAnalyzer? = nil
    
    func reset() {
        source = nil
        analyzer = nil
    }
}
