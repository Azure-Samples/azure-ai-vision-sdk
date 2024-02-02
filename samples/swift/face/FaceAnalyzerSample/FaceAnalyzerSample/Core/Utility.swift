//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI
import AVFoundation
import AzureAIVisionFace

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
    let bitmatpinfo = image.cgImage?.bitmapInfo
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

func RecognitionStatusToString(status: FaceRecognitionStatus) -> String {
    switch status {
    case .notComputed: return LocalizationStrings.recognitionStatusNotComputed
    case .failed: return LocalizationStrings.recognitionStatusFailed
    case .notRecognized: return LocalizationStrings.recognitionStatusNotRecognized
    case .recognized: return LocalizationStrings.recognitionStatusRecognized
    default: return LocalizationStrings.recognitionStatusUnknown
    }
}

func RecognitionFailureToString(reason: FaceRecognitionFailureReason) -> String {
    switch reason {
        case .excessiveFaceBrightness: return LocalizationStrings.recognitionFailureExcessiveFaceBrightness
        case .excessiveImageBlurDetected: return LocalizationStrings.recognitionFailureExcessiveImageBlurDetected
        case .faceEyeRegionNotVisible: return LocalizationStrings.recognitionFailureFaceEyeRegionNotVisible
        case .faceNotFrontal: return LocalizationStrings.recognitionFailureFaceNotFrontal
        case .none: return LocalizationStrings.recognitionFailureNone
        case .faceNotFound: return LocalizationStrings.recognitionFailureFaceNotFound
        case .multipleFaceFound: return LocalizationStrings.recognitionFailureMultipleFaceFound
        case .contentDecodingError: return LocalizationStrings.recognitionFailureContentDecodingError
        case .imageSizeIsTooLarge: return LocalizationStrings.recognitionFailureImageSizeIsTooLarge
        case .imageSizeIsTooSmall: return LocalizationStrings.recognitionFailureImageSizeIsTooSmall
        default: return LocalizationStrings.recognitionFailureGenericFailure
    }
}

func LivenessStatusToString(status: FaceLivenessStatus) -> String {
    switch status {
        case .notComputed: return LocalizationStrings.livenessStatusNotComputed
        case .failed: return LocalizationStrings.livenessStatusFailed
        case .live: return LocalizationStrings.livenessStatusLive
        case .spoof: return LocalizationStrings.livenessStatusSpoof
        case .completedResultQueryableFromService: return LocalizationStrings.livenessStatusCompletedResultQueryableFromService
        default: return LocalizationStrings.livenessStatusUnknown
    }
}

func LivenessFailureReasonToString(reason: FaceLivenessFailureReason) -> String {
    switch reason {
        case .none: return LocalizationStrings.livenessFailureNone
        case .faceMouthRegionNotVisible: return LocalizationStrings.livenessFailureFaceMouthRegionNotVisible
        case .faceEyeRegionNotVisible: return LocalizationStrings.livenessFailureFaceEyeRegionNotVisible
        case .excessiveImageBlurDetected: return LocalizationStrings.livenessFailureExcessiveImageBlurDetected
        case .excessiveFaceBrightness: return LocalizationStrings.livenessFailureExcessiveFaceBrightness
        case .faceWithMaskDetected: return LocalizationStrings.livenessFailureFaceWithMaskDetected
        case .actionNotPerformed: return LocalizationStrings.livenessFailureActionNotPerformed
        case .timedOut: return LocalizationStrings.livenessFailureTimedOut
        case .environmentNotSupported: return LocalizationStrings.livenessFailureEnvironmentNotSupported
        case .unexpectedClientError: return LocalizationStrings.livenessFailureUnexpectedClientError
        case .unexpectedServerError: return LocalizationStrings.livenessFailureUnexpectedServerError
        case .unexpected: return LocalizationStrings.livenessFailureUnexpected
        default: return LocalizationStrings.livenessFailureUnknown
    }
}

func FaceFeedbackToString(feedback: FaceAnalyzingFeedbackForFace) -> String {
    switch feedback {
        case .faceNotCentered: return LocalizationStrings.faceFeedbackFaceNotCentered
        case .lookAtCamera: return LocalizationStrings.faceFeedbackLookAtCamera
        case .moveBack: return LocalizationStrings.faceFeedbackMoveBack
        case .moveCloser: return LocalizationStrings.faceFeedbackMoveCloser
        case .tooMuchMovement: return LocalizationStrings.faceFeedbackTooMuchMovement
        case .attentionNotNeeded: return LocalizationStrings.faceFeedbackAttentionNotNeeded
        default: return LocalizationStrings.faceFeedbackHoldStill
    }
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
