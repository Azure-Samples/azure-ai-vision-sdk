package com.azure.ai.vision.face.deviceattestation;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** ISO-8601 UTC timestamp helpers (always ends in "Z"). */
public final class IsoTime {

    public static String now() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }

    public static String from(Instant value) {
        return DateTimeFormatter.ISO_INSTANT.format(value);
    }

    private IsoTime() {
    }
}
