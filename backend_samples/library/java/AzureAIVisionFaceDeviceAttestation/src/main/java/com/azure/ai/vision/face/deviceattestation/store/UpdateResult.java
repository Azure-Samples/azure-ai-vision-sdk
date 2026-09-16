package com.azure.ai.vision.face.deviceattestation.store;

/** Result of an atomic version-conditional update. */
public enum UpdateResult { APPLIED, CONFLICT, MISSING_OR_EXPIRED }