package com.azure.ai.vision.face.deviceattestation.store;

/** A storage operation failed or its commit outcome is unknown. */
public final class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}