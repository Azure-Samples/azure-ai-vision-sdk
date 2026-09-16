package com.azure.ai.vision.face.deviceattestation;

import java.util.Map;

/** Small typed readers over the session-state map. */
public final class JsonData {

    /** String value, or null when absent / not a string. */
    public static String getString(Map<String, Object> obj, String key) {
        Object node = obj.get(key);
        return node instanceof String s ? s : null;
    }

    /** Boolean value (true only when the node is JSON true). */
    public static boolean getBool(Map<String, Object> obj, String key) {
        Object node = obj.get(key);
        return node instanceof Boolean b && b;
    }

    /** Integral value (accepts any numeric node), or null. */
    public static Long getLong(Map<String, Object> obj, String key) {
        Object node = obj.get(key);
        return node instanceof Number n ? n.longValue() : null;
    }

    private JsonData() {
    }
}
