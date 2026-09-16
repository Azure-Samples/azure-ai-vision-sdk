//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation

/// Errors bubbled out of [CertificateManager] (auth-cert + ephemeral-cert paths).
enum CertificateError: Error, LocalizedError {
    case keyGenerationFailed(underlying: Error?)
    case certificateNotFound(deviceUUID: String)
    case thumbprintCalculationFailed(underlying: Error?)
    case keyNotFound(deviceUUID: String)
    case keychainOperationFailed(status: OSStatus)
    case invalidCertificateData
    case publicKeyExtractionFailed
    case secureEnclaveUnavailable

    var errorDescription: String? {
        switch self {
        case .keyGenerationFailed(let error):
            return "Failed to generate key pair: \(error?.localizedDescription ?? "unknown error")"
        case .certificateNotFound(let uuid):
            return "Certificate not found for device UUID: \(uuid)"
        case .thumbprintCalculationFailed(let error):
            return "Failed to calculate thumbprint: \(error?.localizedDescription ?? "unknown error")"
        case .keyNotFound(let uuid):
            return "Private key not found for device UUID: \(uuid)"
        case .keychainOperationFailed(let status):
            return "Keychain operation failed with status: \(status)"
        case .invalidCertificateData:
            return "Invalid certificate data"
        case .publicKeyExtractionFailed:
            return "Failed to extract public key from certificate"
        case .secureEnclaveUnavailable:
            return "Secure Enclave is unavailable on this device"
        }
    }
}
