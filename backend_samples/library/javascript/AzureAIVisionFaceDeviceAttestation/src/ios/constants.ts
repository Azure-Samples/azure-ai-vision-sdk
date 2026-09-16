/**
 * Pinned roots, aaguids, and OIDs used throughout the App Attest verifier.
 */

/**
 * Apple App Attestation Root CA.
 * https://www.apple.com/certificateauthority/Apple_App_Attestation_Root_CA.pem
 */
export const APPLE_APP_ATTEST_ROOT_CAS = [
  `-----BEGIN CERTIFICATE-----
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
-----END CERTIFICATE-----`,
];

/**
 * Production aaguid is the ASCII bytes "appattest" + 7 NULs.
 * Development aaguid is the ASCII "appattestdevelop".
 * Apple documents both; we only accept development when DEBUG_MODE=true.
 */
export const AAGUID_PROD = Buffer.from('appattest\0\0\0\0\0\0\0', 'utf8'); // 16 bytes
export const AAGUID_DEV = Buffer.from('appattestdevelop', 'utf8'); // 16 bytes

export const NONCE_OID = '1.2.840.113635.100.8.2';

export const RP_ID_HASH_BYTES = 32;
export const FLAGS_OFFSET = RP_ID_HASH_BYTES;
export const SIGN_COUNT_OFFSET = FLAGS_OFFSET + 1;
export const AUTH_DATA_HEADER_BYTES = SIGN_COUNT_OFFSET + 4;
export const AAGUID_OFFSET = AUTH_DATA_HEADER_BYTES;
export const AAGUID_BYTES = 16;
export const CREDENTIAL_ID_LENGTH_OFFSET = AAGUID_OFFSET + AAGUID_BYTES;
export const CREDENTIAL_ID_OFFSET = CREDENTIAL_ID_LENGTH_OFFSET + 2;
