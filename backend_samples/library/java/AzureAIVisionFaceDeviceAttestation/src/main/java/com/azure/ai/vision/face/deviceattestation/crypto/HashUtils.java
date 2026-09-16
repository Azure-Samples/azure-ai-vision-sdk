package com.azure.ai.vision.face.deviceattestation.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers (SHA-256 is guaranteed present on every JVM). */
public final class HashUtils {

    /** SHA-256 over the concatenation of the given byte arrays. */
    public static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                md.update(p);
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private HashUtils() {
    }
}
