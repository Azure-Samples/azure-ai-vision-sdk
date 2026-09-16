package com.azure.ai.vision.face.sample.store;

/**
 * App-owned per-session context, kept SEPARATE from the library's session
 * record: the Face credentials + action the sample uses to poll the liveness
 * result. Never sent to the browser.
 */
public record AppSession(String resource, String apiKey, String action) {
}
