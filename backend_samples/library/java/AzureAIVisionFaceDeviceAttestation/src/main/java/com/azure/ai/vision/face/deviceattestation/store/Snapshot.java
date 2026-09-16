package com.azure.ai.vision.face.deviceattestation.store;

/** Detached record value with an opaque revision assigned by the store. */
public record Snapshot<T>(T value, String version) { }