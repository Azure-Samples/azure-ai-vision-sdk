package com.azure.ai.vision.face.deviceattestation.android;

import com.azure.ai.vision.face.deviceattestation.crypto.CertUtils;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;

/**
 * Android Key Attestation extension parsing (OID 1.3.6.1.4.1.11129.2.1.17).
 * Parses the five leading fields of the KeyDescription SEQUENCE; the
 * attestationChallenge must equal the session challengeHash.
 */
public final class KeymasterExt {
    public static final String KEYMASTER_EXT_OID = "1.3.6.1.4.1.11129.2.1.17";

    private static final int ATTESTATION_VERSION_INDEX = 0;
    private static final int ATTESTATION_SECURITY_LEVEL_INDEX = 1;
    private static final int KEY_MINT_VERSION_INDEX = 2;
    private static final int KEY_MINT_SECURITY_LEVEL_INDEX = 3;
    private static final int ATTESTATION_CHALLENGE_INDEX = 4;

    /** Decoded leading fields of the KeyDescription SEQUENCE. */
    public record KeyDescription(
            int attestationVersion,
            int attestationSecurityLevel,
            int keyMintVersion,
            int keyMintSecurityLevel,
            byte[] attestationChallenge) {
    }

    public static KeyDescription parseKeyDescription(byte[] leafDer) {
        byte[] container = CertUtils.getExtensionValue(leafDer, KEYMASTER_EXT_OID);
        if (container == null) {
            return null;
        }
        try {
            ASN1Sequence sequence = ASN1Sequence.getInstance(container);
            KeyDescription description = new KeyDescription(
                    ASN1Integer.getInstance(sequence.getObjectAt(ATTESTATION_VERSION_INDEX)).intValueExact(),
                    ASN1Enumerated.getInstance(sequence.getObjectAt(ATTESTATION_SECURITY_LEVEL_INDEX)).intValueExact(),
                    ASN1Integer.getInstance(sequence.getObjectAt(KEY_MINT_VERSION_INDEX)).intValueExact(),
                    ASN1Enumerated.getInstance(sequence.getObjectAt(KEY_MINT_SECURITY_LEVEL_INDEX)).intValueExact(),
                    ASN1OctetString.getInstance(sequence.getObjectAt(ATTESTATION_CHALLENGE_INDEX)).getOctets());
            return description.attestationVersion() < 0 || description.attestationSecurityLevel() < 0
                    || description.keyMintVersion() < 0 || description.keyMintSecurityLevel() < 0 ? null : description;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static byte[] extractAttestationChallengeFromCert(byte[] leafDer) {
        KeyDescription kd = parseKeyDescription(leafDer);
        return kd == null ? null : kd.attestationChallenge();
    }

    public static boolean isHardwareAttestationSecurityLevel(KeyDescription description) {
        return description != null
                && (description.attestationSecurityLevel() == 1 || description.attestationSecurityLevel() == 2);
    }

    private KeymasterExt() {
    }
}
