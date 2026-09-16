package com.azure.ai.vision.face.deviceattestation.crypto;

/** A P-256 EC key pair in PEM form (SPKI public key + PKCS8 private key). */
public record EcKeyPair(String publicKey, String privateKey) {
}
