package com.azure.ai.vision.face.deviceattestation.services;

import com.azure.ai.vision.face.deviceattestation.handlers.HandlerOutcome;
import java.util.function.BooleanSupplier;

/** Either success (caller continues) or the exact failure outcome to return. */
public final class IosAssertionCheckResult {

    public final boolean ok;
    public final HandlerOutcome result;
    public final BooleanSupplier commit;

    private IosAssertionCheckResult(boolean ok, HandlerOutcome result, BooleanSupplier commit) {
        this.ok = ok;
        this.result = result;
        this.commit = commit;
    }

    public static IosAssertionCheckResult success(BooleanSupplier commit) {
        return new IosAssertionCheckResult(true, null, commit);
    }

    public static IosAssertionCheckResult failure(HandlerOutcome result) {
        return new IosAssertionCheckResult(false, result, null);
    }
}
