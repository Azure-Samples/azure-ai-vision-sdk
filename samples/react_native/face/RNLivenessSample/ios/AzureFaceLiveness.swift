import Foundation
import SwiftUI
import AzureAIVisionFaceUI
import React

@objc(AzureLivenessManager)
class AzureLivenessManager: RCTEventEmitter {

  private var hasListeners = false

  // MARK: - React Native EventEmitter Support

  override static func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func supportedEvents() -> [String] {
    return ["LivenessResultEvent"]
  }

  override func startObserving() {
    hasListeners = true
  }

  override func stopObserving() {
    hasListeners = false
  }

  // MARK: - Main Method to Start Detection

  @objc(startLivenessDetection:isLiveness:)
  func startLivenessDetection(sessionToken: String, isLiveNess: String) {
    DispatchQueue.main.async {
      guard let rootVC = UIApplication.shared.delegate?.window??.rootViewController else {
        self.sendEvent(withName: "LivenessResultEvent", body: [
          "status": "error",
          "message": "Could not find root view controller"
        ])
        return
      }

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
            resultMap["isLiveNess"] = isLiveNess

          case .failure(let failure):
            resultMap["status"] = "failure"
            resultMap["isLiveNess"] = isLiveNess
            resultMap["message"] = failure.localizedDescription
            if let resultId = failure.resultId {
              resultMap["resultId"] = resultId
            }
          }

          if self.hasListeners {
            self.sendEvent(withName: "LivenessResultEvent", body: resultMap)
          }

          rootVC.dismiss(animated: true, completion: nil)
        }
      )

      let livenessView = FaceLivenessDetectorView(
        result: resultBinding,
        sessionAuthorizationToken: sessionToken
      )

      let controller = UIHostingController(rootView: AnyView(livenessView))
      rootVC.present(controller, animated: true, completion: nil)
    }
  }
}
