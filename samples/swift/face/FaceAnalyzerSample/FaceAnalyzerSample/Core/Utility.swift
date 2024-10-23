//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionCore
import AzureAIVisionFaceUI

public enum PixelFormat
{
    case abgr
    case argb
    case bgra
    case rgba
    
    func getFourCCString()-> String {
        switch self {
        case .abgr:
            return "ABGR"
        case .argb:
            return "ARGB"
        case .bgra:
            return "BGRA"
        case .rgba:
            return "RGBA"
        }
    }
}

extension CGBitmapInfo
{
    public static var byteOrder16Host: CGBitmapInfo {
        return CFByteOrderGetCurrent() == Int(CFByteOrderLittleEndian.rawValue) ? .byteOrder16Little : .byteOrder16Big
    }
    
    public static var byteOrder32Host: CGBitmapInfo {
        return CFByteOrderGetCurrent() == Int(CFByteOrderLittleEndian.rawValue) ? .byteOrder32Little : .byteOrder32Big
    }
}

extension CGBitmapInfo
{
    public var pixelFormat: PixelFormat? {
        
        // AlphaFirst – the alpha channel is next to the red channel, argb and bgra are both alpha first formats.
        // AlphaLast – the alpha channel is next to the blue channel, rgba and abgr are both alpha last formats.
        // LittleEndian – blue comes before red, bgra and abgr are little endian formats.
        // Little endian ordered pixels are BGR (BGRX, XBGR, BGRA, ABGR, BGR).
        // BigEndian – red comes before blue, argb and rgba are big endian formats.
        // Big endian ordered pixels are RGB (XRGB, RGBX, ARGB, RGBA, RGB).
        
        let alphaInfo: CGImageAlphaInfo? = CGImageAlphaInfo(rawValue: self.rawValue & type(of: self).alphaInfoMask.rawValue)
        let alphaFirst: Bool = alphaInfo == .premultipliedFirst || alphaInfo == .first || alphaInfo == .noneSkipFirst
        let alphaLast: Bool = alphaInfo == .premultipliedLast || alphaInfo == .last || alphaInfo == .noneSkipLast
        let endianLittle: Bool = self.contains(.byteOrder32Little)
        
        // This is slippery… while byte order host returns little endian, default bytes are stored in big endian
        // format. Here we just assume if no byte order is given, then simple RGB is used, aka big endian, though…
        
        if alphaFirst && endianLittle {
            return .bgra
        } else if alphaFirst {
            return .argb
        } else if alphaLast && endianLittle {
            return .abgr
        } else if alphaLast {
            return .rgba
        } else {
            return nil
        }
    }
}

extension NSMutableData {
    func append(_ string: String) {
        if let data = string.data(using: .utf8) {
            self.append(data)
        }
    }
}

func getSourceFromImage(image: UIImage) ->VisionFrameSource {
    let width = image.size.width
    let height = image.size.height
    let stride = image.cgImage?.bytesPerRow
    let fourcc = image.cgImage?.bitmapInfo.pixelFormat?.getFourCCString()
    let format = try! VisionFrameFormat(fourCCFormat: fourcc!, width: Int(width), height: Int(height), stride: Int(stride!))
    let source = try! VisionFrameSource(format: format)
    
    return source
}

func getFrameFromImage(image: UIImage) ->VisionFrame {
    let width = image.size.width
    let height = image.size.height
    let stride = image.cgImage?.bytesPerRow
    let rawData = image.cgImage?.dataProvider?.data
    let frame = try! VisionFrame(data: rawData! as Data)
    let fourcc = image.cgImage?.bitmapInfo.pixelFormat?.getFourCCString()
    let data = Data(fourcc!.utf8)
    let hexString = data.map{ String(format:"%x", $0) }.joined()
    let decimal = UInt32(hexString, radix:16)!
    
    // the property values are subject to change, will moved to the internal Vision Source SDK.
    frame.properties?.setPropertyValue(String(decimal), forKey: "frame.format.pixel_format")
    frame.properties?.setPropertyValue(String(Int(height)), forKey: "frame.format.height")
    frame.properties?.setPropertyValue(String(Int(width)), forKey: "frame.format.width")
    frame.properties?.setPropertyValue(String(stride!), forKey: "frame.format.stride")
    frame.properties?.setPropertyValue("SourceKind_Color", forKey: "frame.format.source_kind")
    
    return frame
}

func convertToRGBImage(inputImage: CGImage) -> CGImage? {
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let width = inputImage.width
    let height = inputImage.height

    // Create a bitmap context with RGB format
    guard let context = CGContext(data: nil,
                                  width: width,
                                  height: height,
                                  bitsPerComponent: 8,
                                  bytesPerRow: width * 4,
                                  space: colorSpace,
                                  bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
    else {
        return nil
    }

    // Draw the original image onto the context
    context.draw(inputImage, in: CGRect(x: 0, y: 0, width: width, height: height))

    // Retrieve the converted image from the context
    let outputImage = context.makeImage()

    return outputImage
}

extension UIImage {
    func normalizedImage() -> UIImage {
        if self.imageOrientation == .up {
            return self
        }

        UIGraphicsBeginImageContextWithOptions(self.size, false, self.scale)
        self.draw(in: CGRect(origin: .zero, size: self.size))
        let normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        if let normalizedImage = normalizedImage {
            return normalizedImage
        } else {
            return self
        }
    }
}

extension LivenessDetectionResult {
    var message: String {
        get {
            switch (self) {
            case .success(let result):
                var livenessResultString = "Liveness status: \(result.livenessStatus.localizedDescription)\n"
                
                if let recognitionStatus = result.recognitionStatus
                {
                    livenessResultString += "Verify Result: \(recognitionStatus.localizedDescription)\n"
                    if let matchConfidence = recognitionStatus.matchConfidence {
                        livenessResultString += "Verify Score: \(matchConfidence)\n"
                    }
                }
                return livenessResultString
            case .failure(let error):
                return """
                    Liveness status: Failure
                    Liveness Failure Reason: \(error.livenessError.localizedDescription)
                    Verify Failure Reason: \(error.recognitionError.localizedDescription)
                """
            }
        }
    }
}
