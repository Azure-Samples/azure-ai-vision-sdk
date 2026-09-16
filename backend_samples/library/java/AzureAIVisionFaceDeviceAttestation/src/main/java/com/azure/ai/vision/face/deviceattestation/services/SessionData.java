package com.azure.ai.vision.face.deviceattestation.services;

import java.util.Map;

/** Read record for a loaded session: token, mutable state, and id. */
public record SessionData(String token, Map<String, Object> data, String sid, String version) {
}
