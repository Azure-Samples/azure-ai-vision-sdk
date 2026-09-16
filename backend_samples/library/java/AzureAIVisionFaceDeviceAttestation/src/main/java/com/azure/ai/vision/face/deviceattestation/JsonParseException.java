package com.azure.ai.vision.face.deviceattestation;

/** Thrown when an internal JSON payload cannot be parsed. */
public final class JsonParseException extends RuntimeException {

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
