//
//  ImageMetadata.swift
//  Flash
//
//  Copyright (c) Microsoft Corporation. All rights reserved.
//

struct ImageData : Decodable {
    let fileName: String
}

struct Metadata : Decodable {
    let imageData: [ImageData]
}

struct ImageMetadata : Decodable {
    let metadata: Metadata
}
