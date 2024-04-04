//
//  AppUtility.swift
//  FaceAnalyzerSample
//
//  Created by MSFACE on 11/28/23.
//

import Foundation
import UIKit

// this method is for sample App demonstration purpose, the session token should be obtained in customer backend
func obtainToken(usingEndpoint endpoint: String,
                 key: String, withVerify: Bool, sendResultsToClient: Bool, verifyImage: Data? = nil) -> String? {
    var createSessionUri = URL(string: endpoint + "/face/v1.1-preview.1/detectLiveness/singleModal/sessions")!
    if (withVerify)
    {
        createSessionUri = URL(string: endpoint + "/face/v1.1-preview.1/detectLivenessWithVerify/singleModal/sessions")!
    }
    var request = URLRequest(url: createSessionUri)
    request.httpMethod = "POST"

    request.setValue(key, forHTTPHeaderField: "Ocp-Apim-Subscription-Key")

    let parameters: [String: Any] = [
        "livenessOperationMode": "Passive",
        "sendResultsToClient": sendResultsToClient,
        "deviceCorrelationId": UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString,
    ]

    do {
        let jsonData = try JSONSerialization.data(withJSONObject: parameters, options: [])
        if (withVerify && verifyImage != nil) {
            let boundary = UUID().uuidString
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            
            var body = Data()
            
            // Append JSON part
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Type: application/json; charset=utf-8\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"Parameters\"\r\n\r\n".data(using: .utf8)!)
            body.append(jsonData)
            body.append("\r\n".data(using: .utf8)!)

            // Append image data
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"VerifyImage\"; filename=\"VerifyImage\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
            body.append(verifyImage!)
            body.append("\r\n".data(using: .utf8)!)
            
            // End of multipart/form-data
            body.append("--\(boundary)--\r\n".data(using: .utf8)!)
            request.httpBody = body
        }
        else {
            request.httpBody = jsonData
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
    }
    catch {
        print("Error encoding parameters: \(error)")
        return nil
    }

    let session = URLSession.shared
    let group = DispatchGroup()

    group.enter()
    var authToken: String?

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
                    if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                        if let authTokenValue = json["authToken"] as? String {
                            authToken = authTokenValue
                            print(authToken)
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

    return authToken
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

