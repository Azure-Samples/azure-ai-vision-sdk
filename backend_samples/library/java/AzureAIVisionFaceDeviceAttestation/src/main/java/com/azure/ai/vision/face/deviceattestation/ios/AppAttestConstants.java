package com.azure.ai.vision.face.deviceattestation.ios;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Pinned roots, aaguids, and OIDs used throughout the App Attest verifier. */
public final class AppAttestConstants {

    /**
     * Apple App Attestation Root CA.
     * https://www.apple.com/certificateauthority/Apple_App_Attestation_Root_CA.pem
     */
    public static final String APPLE_APP_ATTEST_ROOT_CA_PEM = """
        -----BEGIN CERTIFICATE-----
        MIICITCCAaegAwIBAgIQC/O+DvHN0uD7jG5yH2IXmDAKBggqhkjOPQQDAzBSMSYw
        JAYDVQQDDB1BcHBsZSBBcHAgQXR0ZXN0YXRpb24gUm9vdCBDQTETMBEGA1UECgwK
        QXBwbGUgSW5jLjETMBEGA1UECAwKQ2FsaWZvcm5pYTAeFw0yMDAzMTgxODMyNTNa
        Fw00NTAzMTUwMDAwMDBaMFIxJjAkBgNVBAMMHUFwcGxlIEFwcCBBdHRlc3RhdGlv
        biBSb290IENBMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9y
        bmlhMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAERTHhmLW07ATaFQIEVwTtT4dyctdh
        NbJhFs/Ii2FdCgAHGbpphY3+d8qjuDngIN3WVhQUBHAoMeQ/cLiP1sOUtgjqK9au
        Yen1mMEvRq9Sk3Jm5X8U62H+xTD3FE9TgS41o0IwQDAPBgNVHRMBAf8EBTADAQH/
        MB0GA1UdDgQWBBSskRBTM72+aEH/pwyp5frq5eWKoTAOBgNVHQ8BAf8EBAMCAQYw
        CgYIKoZIzj0EAwMDaAAwZQIwQgFGnByvsiVbpTKwSga0kP0e8EeDS4+sQmTvb7vn
        53O5+FRXgeLhpJ06ysC5PrOyAjEAp5U4xDgEgllF7En3VcE3iexZZtKeYnpqtijV
        oyFraWVIyd/dganmrduC1bmTBGwD
        -----END CERTIFICATE-----
        """;

    public static final List<String> APPLE_APP_ATTEST_ROOT_CAS = List.of(APPLE_APP_ATTEST_ROOT_CA_PEM);

    /** Production aaguid: the ASCII bytes "appattest" + 7 NULs (16 bytes). */
    public static final byte[] AAGUID_PROD = {
        'a', 'p', 'p', 'a', 't', 't', 'e', 's', 't',
        0, 0, 0, 0, 0, 0, 0,
    };

    /** Development aaguid: the ASCII "appattestdevelop" (16 bytes). */
    public static final byte[] AAGUID_DEV = "appattestdevelop".getBytes(StandardCharsets.US_ASCII);

    public static final String NONCE_OID = "1.2.840.113635.100.8.2";

    static final int RP_ID_HASH_BYTES = 32;
    static final int FLAGS_OFFSET = RP_ID_HASH_BYTES;
    static final int SIGN_COUNT_OFFSET = FLAGS_OFFSET + Byte.BYTES;
    static final int AUTH_DATA_HEADER_BYTES = SIGN_COUNT_OFFSET + Integer.BYTES;
    static final int AAGUID_OFFSET = AUTH_DATA_HEADER_BYTES;
    static final int AAGUID_BYTES = 16;
    static final int CREDENTIAL_ID_LENGTH_OFFSET = AAGUID_OFFSET + AAGUID_BYTES;
    static final int CREDENTIAL_ID_OFFSET = CREDENTIAL_ID_LENGTH_OFFSET + Short.BYTES;

    private AppAttestConstants() {
    }
}
