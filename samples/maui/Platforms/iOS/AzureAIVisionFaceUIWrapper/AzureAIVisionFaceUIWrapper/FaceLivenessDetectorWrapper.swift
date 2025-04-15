//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

import AzureAIVisionFaceUI
import Combine
import SwiftUI
import UIKit

@objc public class FaceLivenessDetectorWrapper: NSObject, ObservableObject {
    var swiftUIView: FaceLivenessDetectorView?
    var hostingController: UIHostingController<FaceLivenessDetectorView>?
    var result: LivenessDetectionResult?
    
    @objc public var uiView: UIView? { hostingController?.view }
    
    private override init() { }
    
    @objc public init(
            sessionAuthorizationToken: String,
            delegate: FaceLivenessDetectorWrapperDelegate) {
        super.init()
        
        swiftUIView = FaceLivenessDetectorView(
            result: Binding<LivenessDetectionResult?>(
                get: {
                    self.result
                },
                set: {
                    self.result = $0
                    if let result = $0 {
                        delegate.resultHasChanged(result: LivenessDetectionResultWrapper(result: result))
                    }
                }),
            sessionAuthorizationToken: sessionAuthorizationToken)
        guard let rootView = swiftUIView else { return }
        hostingController = UIHostingController(rootView: rootView)
    }
}
