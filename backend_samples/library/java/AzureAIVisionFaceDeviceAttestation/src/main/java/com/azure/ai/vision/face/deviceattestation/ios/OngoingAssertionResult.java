package com.azure.ai.vision.face.deviceattestation.ios;

/** Result of a per-call App Attest assertion verification. */
public final class OngoingAssertionResult {

    public final boolean ok;
    public final String reason;
    public final String message;
    public final long signCount;

    private OngoingAssertionResult(boolean ok, String reason, String message, long signCount) {
        this.ok = ok;
        this.reason = reason;
        this.message = message;
        this.signCount = signCount;
    }

    public static OngoingAssertionResult success(long signCount) {
        return new OngoingAssertionResult(true, null, null, signCount);
    }

    public static OngoingAssertionResult failure(String reason, String message) {
        return new OngoingAssertionResult(false, reason, message, 0);
    }

    public static OngoingAssertionResult failure(String reason, String message, long signCount) {
        return new OngoingAssertionResult(false, reason, message, signCount);
    }
}
