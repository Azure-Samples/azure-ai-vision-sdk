using System.Formats.Asn1;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>Decoded leading fields of the KeyDescription SEQUENCE.</summary>
internal sealed class KeyDescription
{
    public int AttestationVersion { get; init; }
    public int AttestationSecurityLevel { get; init; }
    public int KeyMintVersion { get; init; }
    public int KeyMintSecurityLevel { get; init; }
    /// <summary>The OCTET STRING bytes — for the verifier this is the session nonce.</summary>
    public required byte[] AttestationChallenge { get; init; }
}

/// <summary>
/// Android Key Attestation extension parsing (OID 1.3.6.1.4.1.11129.2.1.17).
/// Parses the five leading fields of the KeyDescription SEQUENCE; the
/// attestationChallenge must equal the session challengeHash.
/// </summary>
internal static class KeymasterExt
{
    public const string KeymasterExtOid = "1.3.6.1.4.1.11129.2.1.17";

    public static KeyDescription? ParseKeyDescription(byte[] leafDer)
    {
        var container = CertUtils.GetExtensionValue(leafDer, KeymasterExtOid);
        if (container is null)
        {
            return null;
        }
        try
        {
            var reader = new AsnReader(container, AsnEncodingRules.DER);
            var sequence = reader.ReadSequence();
            reader.ThrowIfNotEmpty();
            var description = new KeyDescription
            {
                AttestationVersion = (int)sequence.ReadInteger(),
                AttestationSecurityLevel = (int)sequence.ReadEnumeratedValue<SecurityLevel>(),
                KeyMintVersion = (int)sequence.ReadInteger(),
                KeyMintSecurityLevel = (int)sequence.ReadEnumeratedValue<SecurityLevel>(),
                AttestationChallenge = sequence.ReadOctetString(),
            };
            return description.AttestationVersion < 0 || description.AttestationSecurityLevel < 0 ||
                description.KeyMintVersion < 0 || description.KeyMintSecurityLevel < 0 ? null : description;
        }
        catch (Exception exception) when (exception is AsnContentException or OverflowException)
        {
            return null;
        }
    }

    public static byte[]? ExtractAttestationChallengeFromCert(byte[] leafDer)
        => ParseKeyDescription(leafDer)?.AttestationChallenge;

    public static bool IsHardwareAttestationSecurityLevel(KeyDescription? description)
        => description?.AttestationSecurityLevel is
            (int)SecurityLevel.TrustedEnvironment or (int)SecurityLevel.StrongBox;

    private enum SecurityLevel
    {
        Software = 0,
        TrustedEnvironment = 1,
        StrongBox = 2,
    }

}
