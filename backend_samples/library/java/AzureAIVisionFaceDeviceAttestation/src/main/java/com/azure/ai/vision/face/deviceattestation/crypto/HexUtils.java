package com.azure.ai.vision.face.deviceattestation.crypto;

import java.util.HexFormat;

/** Lowercase-hex encode/decode, matching the wire protocol's hex conventions. */
public final class HexUtils {

    /** Encode bytes as lowercase hex. */
    public static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /** Decode a hex string (any case) to bytes. */
    public static byte[] fromHex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private HexUtils() {
    }
}
