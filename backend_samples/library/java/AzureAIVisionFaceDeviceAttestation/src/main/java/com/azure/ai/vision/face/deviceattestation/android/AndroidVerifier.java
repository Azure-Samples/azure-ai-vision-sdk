package com.azure.ai.vision.face.deviceattestation.android;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.services.AttestationMessageData;
import com.azure.ai.vision.face.deviceattestation.services.AuthVerificationResult;

/**
 * Android Play Integrity + Key Attestation verifier — the seam the register
 * handler depends on. Delegates to {@link AndroidVerification}.
 */
public final class AndroidVerifier {

    /** Verify Android Key Attestation + Play Integrity. */
    public static AuthVerificationResult verify(AttestationContext ctx, AttestationMessageData messageData, String attestJson) {
        return AndroidVerification.verify(ctx.config, ctx.logger, messageData, attestJson);
    }

    private AndroidVerifier() {
    }
}
