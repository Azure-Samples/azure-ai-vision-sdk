package com.azure.ai.vision.face.deviceattestation.services;

/** Result of saving a certificate: its thumbprint and whether it was newly created. */
public record SaveCertificateResult(String thumbprint, boolean isNew) {
}
