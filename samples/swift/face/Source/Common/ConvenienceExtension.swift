//
//  ConvenienceExtensions.swift
//
//
//  Copyright (c) Microsoft Corporation. All rights reserved.
//

import Foundation
import CoreVideo
import AVFoundation
import UIKit
import SwiftUI

extension JSONDecoder {
    static let shared = JSONDecoder()
}

extension JSONEncoder {
    static let shared = JSONEncoder(outputFormatting: .prettyPrinted)

    convenience init(outputFormatting: OutputFormatting) {
        self.init()
        self.outputFormatting = outputFormatting
    }
}

extension JSONSerialization {
    static let shared = JSONSerialization()
}

extension FileManager {
    static var documentDirectory: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

extension UIView {
    func findParent<T>() -> T? {
        if let nextResponder
            = self.next as? T {
            return nextResponder
        } else if let nextResponder = self.next as? UIView {
            let next: T? = nextResponder.findParent()
            return next
        } else {
            return nil
        }
    }
}

extension NSMutableData {
    func appendString(_ string: String) {
        append(string.data(using: String.Encoding.utf8, allowLossyConversion: true)!)
    }
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
