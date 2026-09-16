//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

/// Error thrown by [hexStringToBytes] for malformed input.
enum HexDecodingError: Error, CustomStringConvertible {
    case oddLength(Int)
    case invalidCharacter(index: Int, value: String)

    var description: String {
        switch self {
        case .oddLength(let len):
            return "Hex string must have even length, got \(len)"
        case .invalidCharacter(let index, let value):
            return "Invalid hex character at index \(index): '\(value)'"
        }
    }
}

/**
 * Decodes a hex string to its raw bytes.
 *
 * Used to feed pre-hashed challenges and thumbprints to App Attest as raw
 * 32-byte blobs (mirrors `Buffer.from(hex, 'hex')` on the server side).
 *
 * Throws on odd-length input or invalid characters. The previous lenient
 * implementation silently truncated odd-length input and silently dropped
 * invalid characters — fine for the signature to compute locally, but the
 * server would then reject the App Attest assertion with no clue why.
 */
func hexStringToBytes(_ hex: String) throws -> Data {
    guard hex.count % 2 == 0 else {
        throw HexDecodingError.oddLength(hex.count)
    }
    var bytes = Data(capacity: hex.count / 2)
    var i = hex.startIndex
    var index = 0
    while i < hex.endIndex {
        let next = hex.index(i, offsetBy: 2)
        let pair = hex[i..<next]
        guard let b = UInt8(pair, radix: 16) else {
            throw HexDecodingError.invalidCharacter(index: index, value: String(pair))
        }
        bytes.append(b)
        i = next
        index += 2
    }
    return bytes
}
