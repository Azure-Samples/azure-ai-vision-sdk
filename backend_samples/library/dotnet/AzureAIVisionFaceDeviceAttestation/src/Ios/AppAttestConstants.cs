using System.Text;

namespace Azure.AI.Vision.Face.DeviceAttestation.Ios;

/// <summary>Pinned roots, aaguids, and OIDs used throughout the App Attest verifier.</summary>
internal static class AppAttestConstants
{
    /// <summary>
    /// Apple App Attestation Root CA.
    /// https://www.apple.com/certificateauthority/Apple_App_Attestation_Root_CA.pem
    /// </summary>
    public const string AppleAppAttestRootCaPem =
        """
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

    public static readonly string[] AppleAppAttestRootCAs = { AppleAppAttestRootCaPem };

    /// <summary>Production aaguid: the ASCII bytes "appattest" + 7 NULs (16 bytes).</summary>
    public static readonly byte[] AaguidProd =
    {
        (byte)'a', (byte)'p', (byte)'p', (byte)'a', (byte)'t', (byte)'t', (byte)'e', (byte)'s', (byte)'t',
        0, 0, 0, 0, 0, 0, 0,
    };

    /// <summary>Development aaguid: the ASCII "appattestdevelop" (16 bytes).</summary>
    public static readonly byte[] AaguidDev = Encoding.ASCII.GetBytes("appattestdevelop");

    public const string NonceOid = "1.2.840.113635.100.8.2";

    public const int RpIdHashBytes = 32;
    public const int FlagsOffset = RpIdHashBytes;
    public const int SignCountOffset = FlagsOffset + sizeof(byte);
    public const int AuthDataHeaderBytes = SignCountOffset + sizeof(uint);
    public const int AaguidOffset = AuthDataHeaderBytes;
    public const int AaguidBytes = 16;
    public const int CredentialIdLengthOffset = AaguidOffset + AaguidBytes;
    public const int CredentialIdOffset = CredentialIdLengthOffset + sizeof(ushort);
}
