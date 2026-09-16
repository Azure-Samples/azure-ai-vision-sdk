package com.azure.ai.vision.face.deviceattestation.handlers;

/**
 * Typed result of every attestation route method. Splits the CLIENT-facing
 * suggestion ({@link #status} + {@link #body}) from HOST-facing metadata
 * ({@link #ok}, {@link #code}, {@link #message}, {@link #data}) the host uses to
 * decide what to return.
 * <p>
 * The host typically serializes {@link #body} with the suggested {@link #status},
 * but may inspect {@link #ok}/{@link #code} to substitute a message, hide 5xx
 * internals, or act on {@link #data} (e.g. the client digest).
 */
public final class HandlerOutcome {

    /** True on success (2xx). Lets the host branch without parsing {@link #status}. */
    public final boolean ok;

    /** Stable machine-readable code: "OK" on success, else the failure reason. */
    public final String code;

    /** Suggested HTTP status; the host MAY override it. */
    public final int status;

    /**
     * Suggested client-facing JSON body (a success payload or an
     * {@link ErrorBody}). Declared as {@link Object} so the host's serializer
     * emits the runtime type's fields.
     */
    public final Object body;

    /** Human-readable detail for host logging/decisions; not required to be shown. May be null. */
    public final String message;

    /**
     * Endpoint-specific host-facing result (e.g. the register verdict or the
     * digest's client digest). Null for endpoints without host-facing data.
     */
    public final Object data;

    public HandlerOutcome(boolean ok, String code, int status, Object body, String message, Object data) {
        this.ok = ok;
        this.code = code;
        this.status = status;
        this.body = body;
        this.message = message;
        this.data = data;
    }
}
