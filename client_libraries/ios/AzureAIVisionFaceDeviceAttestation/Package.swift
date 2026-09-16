// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "AzureAIVisionFaceDeviceAttestation",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(
            name: "AzureAIVisionFaceDeviceAttestation",
            targets: ["AzureAIVisionFaceDeviceAttestation"]
        ),
    ],
    targets: [
        .target(
            name: "AzureAIVisionFaceDeviceAttestation",
            linkerSettings: [
                .linkedFramework("CryptoKit"),
                .linkedFramework("DeviceCheck"),
                .linkedFramework("Security"),
            ]
        ),
    ]
)