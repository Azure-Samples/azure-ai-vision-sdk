//
//  AppUtility.swift
//  FaceAnalyzerSample
//
//  Created by MSFACE on 11/28/23.
//

import Foundation
import UIKit

enum LivenessMode {
    case passive
    case passiveActive

    var livenessOperationMode: String {
        switch self {
        case .passive:
            return "Passive"
        case .passiveActive:
            return "PassiveActive"
        }
    }
}

fileprivate var apiVersion = "v1.2"

// this method is for sample App demonstration purpose, the session token should be obtained in customer backend
func obtainToken(usingEndpoint endpoint: String,
                 key: String, withVerify: Bool, verifyImage: Data? = nil, livenessOperationMode: String = "PassiveActive") throws -> (token: String, id: String, type: String)? {
    let type = withVerify ? "detectLivenessWithVerify" : "detectLiveness"
    let createSessionUri = URL(string: endpoint + "/face/\(apiVersion)/\(type)-sessions")!
    var request = URLRequest(url: createSessionUri)
    request.httpMethod = "POST"

    request.setValue(key, forHTTPHeaderField: "Ocp-Apim-Subscription-Key")
    request.setValue("sample=swift-face-ios-sdk;appversion=1.1", forHTTPHeaderField: "X-MS-AZSDK-Telemetry")

    let parameters: [String: Any] = [
        "livenessOperationMode": livenessOperationMode,
        "deviceCorrelationId": UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString,
        "userCorrelationId": UUID().uuidString,
    ]

    do {
        let jsonData = try JSONSerialization.data(withJSONObject: parameters, options: [])
        if (withVerify) {
            let boundary = UUID().uuidString
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

            var body = Data()

            for (name, content) in parameters {
                body.append("--\(boundary)\r\n".data(using: .utf8)!)
                body.append("Content-Type: text/plain; charset=utf-8\r\n".data(using: .utf8)!)
                body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
                body.append(String(describing: content).data(using: .utf8)!)
                body.append("\r\n".data(using: .utf8)!)
            }

            if let verifyImage = verifyImage {
                body.append("--\(boundary)\r\n".data(using: .utf8)!)
                body.append("Content-Disposition: form-data; name=\"verifyImage\"; filename=\"VerifyImage\"\r\n".data(using: .utf8)!)
                body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
                body.append(verifyImage)
                body.append("\r\n".data(using: .utf8)!)
            }

            body.append("--\(boundary)--\r\n".data(using: .utf8)!)
            request.httpBody = body
        } else {
            request.httpBody = jsonData
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
    } catch {
        throw NSError(domain: "obtainToken", code: 1, userInfo: [NSLocalizedDescriptionKey: "Encoding parameters failed: \(error.localizedDescription)"])
    }

    let session = URLSession.shared
    let group = DispatchGroup()

    group.enter()
    var auth: (token: String, id: String, type: String)? = nil
    var caughtError: Error? = nil

    let task: URLSessionTask = session.dataTask(with: request) { data, response, error in
        defer { group.leave() }

        if let error = error {
            print("Error: \(error)")
            caughtError = error
            return
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            caughtError = NSError(domain: "obtainToken", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
            return
        }

        if (200..<300).contains(httpResponse.statusCode) {
            if let data = data {
                do {
                    if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                       let authToken = json["authToken"] as? String,
                       let sessionId = json["sessionId"] as? String {
                        auth = (token: authToken, id: sessionId, type: type)
                    }
                } catch {
                    caughtError = NSError(domain: "obtainToken", code: 3, userInfo: [NSLocalizedDescriptionKey: "JSON parsing failed: \(error.localizedDescription)"])
                }
            }
        } else {
            print("Error status code: \(httpResponse.statusCode)")
            let message = String(data: data ?? Data(), encoding: .utf8) ?? "Unknown error"
            caughtError = NSError(domain: "obtainToken", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: message])
        }
    }

    task.resume()
    group.wait()

    if let error = caughtError {
        throw error
    }

    return auth
}

typealias ServiceResult = (livenessDecision: String, verifyResult: (isIdentical: Bool, matchConfidence: Double)?)

func obtainResult(usingEndpoint endpoint: String,
                  key: String, withVerify: Bool, sessionId: String) -> ServiceResult? {
    let type = withVerify ? "detectLivenessWithVerify" : "detectLiveness"
    let getSessionUri = URL(string: endpoint + "/face/\(apiVersion)/\(type)-sessions/\(sessionId)")! 
    var request = URLRequest(url: getSessionUri)
    request.httpMethod = "GET"

    request.setValue(key, forHTTPHeaderField: "Ocp-Apim-Subscription-Key")

    let session = URLSession.shared
    let group = DispatchGroup()

    group.enter()
    var result: ServiceResult? = nil

    let task: URLSessionTask = session.dataTask(with: request) { data, response, error in
        defer {
            group.leave()
        }

        if let error = error {
            print("Error: \(error)")
            return
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            print("Invalid response")
            return
        }

        if (200..<300).contains(httpResponse.statusCode) {
            if let data = data {
                do {
                    if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                        let sessionResults = json["results"] as? [String: Any],
                        let sessionAttempts = sessionResults["attempts"] as? [Any],
                        let sessionAttempt = sessionAttempts.first as? [String: Any],
                        let sessionResult = sessionAttempt["result"] as? [String: Any],
                        let livenessDecision = sessionResult["livenessDecision"] as? String {
                        result = ServiceResult(livenessDecision: livenessDecision, verifyResult: nil)
                        if let verifyResult = sessionResult["verifyResult"] as? [String: Any],
                            let isIdentical = verifyResult["isIdentical"] as? Bool,
                            let matchConfidence = verifyResult["matchConfidence"] as? Double {
                            result?.verifyResult = (isIdentical: isIdentical, matchConfidence: matchConfidence)
                        }
                    }
                } catch {
                    print("Error parsing JSON: \(error)")
                }
            }
        } else {
            print("Error status code: \(httpResponse.statusCode)")
        }
    }

    task.resume()
    group.wait()

    return result
}

func loadDataFromFile(sessionData: SessionData) {
    let fileManager = FileManager.default
    guard let directory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
        return
    }

    let fileURL = directory.appendingPathComponent("endpoint_key.txt")

    if let data = try? Data(contentsOf: fileURL),
        let content = String(data: data, encoding: .utf8) {
        let components = content.split(separator: "\n")
        if components.count == 2 {
            sessionData.endpoint = String(components[0])
            sessionData.key = String(components[1])
        }
    }
}

func saveDataToFile(sessionData: SessionData) {
    let fileManager = FileManager.default
    guard let directory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
        return
    }

    let fileURL = directory.appendingPathComponent("endpoint_key.txt")

    let data = "\(sessionData.endpoint)\n\(sessionData.key)".data(using: .utf8)
    try? data?.write(to: fileURL)
}

