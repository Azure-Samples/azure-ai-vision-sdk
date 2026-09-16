package com.azure.ai.vision.face.deviceattestation.services;

import java.util.Locale;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.IsoTime;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.android.AndroidVerifier;
import com.azure.ai.vision.face.deviceattestation.ios.IosVerifier;

/** Dispatches attestation verification to the iOS or Android verifier. */
public final class AuthVerification {

    public static AuthVerificationResult verifyAuthBySystem(
            AttestationContext ctx,
            AttestationMessageData messageData,
            String attestJson) {
        String systemLower = messageData.system.toLowerCase(Locale.ROOT);

        ctx.logger.trackEvent("AuthVerification.Dispatch",
                Maps.of("platform", systemLower, "clientId", messageData.clientId, "attestJsonLength", attestJson.length()), null);

        if (systemLower.equals("ios")) {
            return IosVerifier.verify(ctx, messageData, attestJson);
        }
        if (systemLower.equals("android")) {
            return AndroidVerifier.verify(ctx, messageData, attestJson);
        }

        ctx.logger.trackEvent("AuthVerification.UnsupportedSystem",
                Maps.of("platform", messageData.system, "clientId", messageData.clientId), null);
        return AuthVerificationResult.builder()
                .verified(false)
                .platform("unknown")
                .message("Unsupported system: " + messageData.system)
                .timestamp(IsoTime.now())
                .build();
    }

    private AuthVerification() {
    }
}
