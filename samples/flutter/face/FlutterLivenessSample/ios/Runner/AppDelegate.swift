import Flutter
import UIKit


@main
@objc class AppDelegate: FlutterAppDelegate {
    private let channelName = "azure_face_liveness_channel"

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let controller = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(name: channelName, binaryMessenger: controller.binaryMessenger)

        channel.setMethodCallHandler { (call, result) in
            if call.method == "startLiveness" {
                guard let args = call.arguments as? [String: Any],
                      let token = args["sessionToken"] as? String else {
                    result(FlutterError(code: "INVALID_ARGS", message: "Missing sessionToken", details: nil))
                    return
                }

                let livenessController = LivenessViewHostingController(sessionToken: token, flutterResult: result)
                controller.present(livenessController, animated: true, completion: nil)
            }
        }
        GeneratedPluginRegistrant.register(with: self)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}