package com.azure.ai.vision.face.deviceattestation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny ordered-map builder for telemetry properties and JSON-shaped values. */
public final class Maps {

    /** Build an insertion-ordered map from alternating key/value pairs. */
    public static Map<String, Object> of(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Maps() {
    }
}
