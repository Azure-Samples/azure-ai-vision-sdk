import UIKit
import SwiftUI
import AzureAIVisionFaceUI

class LivenessViewHostingController: UIHostingController<AnyView> {
    
    var sessionToken: String
    var flutterResult: FlutterResult?
    @Environment(\.presentationMode) var presentationMode
    
    @objc required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    init(sessionToken: String, flutterResult: @escaping FlutterResult) {
        self.sessionToken = sessionToken
        self.flutterResult = flutterResult
        
        // Create the binding result object
        let resultBinding = Binding<LivenessDetectionResult?>(
            get: { nil },
            set: { result in
                guard let result = result else { return }
                
                var resultMap: [String: Any] = [:]
                
                switch result {
                case .success(let success):
                    resultMap["status"] = "success"
                    resultMap["resultId"] = success.resultId
                    resultMap["digest"] = success.digest
                    
                case .failure(let failure):
                    resultMap["status"] = "failure"
                    resultMap["message"] = failure.localizedDescription
                    if let resultId = failure.resultId {
                        resultMap["resultId"] = resultId
                    }
                }

                flutterResult(resultMap)
                
                // Dismiss the controller
                UIApplication.shared.windows.first?.rootViewController?.dismiss(animated: true, completion: nil)
            }
        )
        
        // Create the SwiftUI view
        let view = FaceLivenessDetectorView(result: resultBinding,
                                            sessionAuthorizationToken: sessionToken)
        
        super.init(rootView: AnyView(view))
    }
}
