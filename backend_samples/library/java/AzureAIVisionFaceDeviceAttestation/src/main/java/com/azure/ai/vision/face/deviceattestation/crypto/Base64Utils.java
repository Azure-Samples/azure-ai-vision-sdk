package com.azure.ai.vision.face.deviceattestation.crypto;

import java.util.Base64;

/** URL-safe / padded base64 decoding used across the wire protocol. */
public final class Base64Utils {

    /** Decode standard or URL-safe base64, tolerating missing padding. */
    public static byte[] decode(String value) {
        String normalized = value.replace('-', '+').replace('_', '/');
        int remainder = normalized.length() % 4;
        if (remainder != 0) {
            normalized = normalized + "====".substring(remainder);
        }
        return Base64.getDecoder().decode(normalized);
    }

    private Base64Utils() {
    }
}
