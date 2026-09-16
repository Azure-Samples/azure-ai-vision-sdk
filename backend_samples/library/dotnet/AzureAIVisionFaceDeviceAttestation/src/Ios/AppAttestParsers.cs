using System.Buffers.Binary;
using System.Formats.Asn1;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using static Azure.AI.Vision.Face.DeviceAttestation.Ios.AppAttestConstants;

namespace Azure.AI.Vision.Face.DeviceAttestation.Ios;

/// <summary>Decoded shape of the CBOR attestation object.</summary>
internal sealed class AppAttestObject
{
    public required string Fmt { get; init; }
    public required byte[] AuthData { get; init; }
    public required byte[] CredCertDer { get; init; }
    public required byte[] IntermediateDer { get; init; }
    public int ReceiptLength { get; init; }
    public byte[]? Receipt { get; init; }
}

/// <summary>Decoded shape of the CBOR assertion object.</summary>
internal sealed class AppAttestAssertionObject
{
    public required byte[] Signature { get; init; }
    public required byte[] AuthenticatorData { get; init; }
}

/// <summary>Decoders for the two CBOR payloads Apple's App Attest APIs return.</summary>
internal static class AppAttestParsers
{
    /// <summary>Decode the base64-CBOR attestation token (DCAppAttestService.attestKey).</summary>
    public static AppAttestObject ParseAppAttestToken(string tokenB64)
    {
        var tokenBuf = Base64Utils.Decode(tokenB64);
        var (topRaw, _) = Cbor.Decode(tokenBuf);
        if (topRaw is not Dictionary<object, object?> top)
        {
            throw new FormatException("top-level CBOR is not a map");
        }

        if (MapGet(top, "fmt") is not string fmt)
        {
            throw new FormatException("missing fmt");
        }
        if (MapGet(top, "attStmt") is not Dictionary<object, object?> attStmt)
        {
            throw new FormatException("missing attStmt");
        }
        if (MapGet(top, "authData") is not byte[] authData)
        {
            throw new FormatException("missing authData");
        }
        if (MapGet(attStmt, "x5c") is not List<object?> x5c || x5c.Count < 2)
        {
            throw new FormatException("attStmt.x5c must have >= 2 certs");
        }
        if (x5c[0] is not byte[] credCertDer || x5c[1] is not byte[] intermediateDer)
        {
            throw new FormatException("attStmt.x5c entries must be byte strings");
        }

        var receipt = MapGet(attStmt, "receipt") as byte[];
        return new AppAttestObject
        {
            Fmt = fmt,
            AuthData = authData,
            CredCertDer = credCertDer,
            IntermediateDer = intermediateDer,
            ReceiptLength = receipt?.Length ?? 0,
            Receipt = receipt,
        };
    }

    /// <summary>Decode the base64-CBOR assertion (DCAppAttestService.generateAssertion).</summary>
    public static AppAttestAssertionObject ParseAppAttestAssertion(string assertionB64)
    {
        var buf = Base64Utils.Decode(assertionB64);
        var (topRaw, _) = Cbor.Decode(buf);
        if (topRaw is not Dictionary<object, object?> top)
        {
            throw new FormatException("assertion top-level CBOR is not a map");
        }
        if (MapGet(top, "signature") is not byte[] signature)
        {
            throw new FormatException("assertion missing signature");
        }
        if (MapGet(top, "authenticatorData") is not byte[] authenticatorData)
        {
            throw new FormatException("assertion missing authenticatorData");
        }
        if (authenticatorData.Length < AuthDataHeaderBytes)
        {
            throw new FormatException($"assertion authenticatorData is {authenticatorData.Length} bytes, < {AuthDataHeaderBytes}");
        }
        return new AppAttestAssertionObject { Signature = signature, AuthenticatorData = authenticatorData };
    }

    /// <summary>Decode the first 37 bytes: rpIdHash(32) || flags(1) || signCount(4).</summary>
    public static (byte[] RpIdHash, int Flags, long SignCount) ParseAssertionAuthData(byte[] authenticatorData)
    {
        if (authenticatorData.Length < AuthDataHeaderBytes)
        {
            throw new FormatException($"authenticatorData is {authenticatorData.Length} bytes, < {AuthDataHeaderBytes}");
        }
        return (authenticatorData[..RpIdHashBytes], authenticatorData[FlagsOffset],
            BinaryPrimitives.ReadUInt32BigEndian(authenticatorData.AsSpan(SignCountOffset)));
    }

    /// <summary>
    /// Pull the nonce OCTET STRING out of credCert extension OID
    /// 1.2.840.113635.100.8.2. Structure:
    ///   Extension ::= SEQUENCE { extnID OID, extnValue OCTET STRING }
    ///   extnValue (DER) = SEQUENCE { [1] EXPLICIT OCTET STRING nonce }
    /// </summary>
    public static byte[]? ExtractNonceFromCredCert(byte[] credCertDer)
    {
        var container = CertUtils.GetExtensionValue(credCertDer, AppAttestConstants.NonceOid);
        if (container is null)
        {
            return null;
        }
        try
        {
            var reader = new AsnReader(container, AsnEncodingRules.DER);
            var sequence = reader.ReadSequence();
            var tagged = sequence.ReadSequence(new Asn1Tag(TagClass.ContextSpecific, 1, isConstructed: true));
            var nonce = tagged.ReadOctetString();
            tagged.ThrowIfNotEmpty();
            sequence.ThrowIfNotEmpty();
            reader.ThrowIfNotEmpty();
            return nonce;
        }
        catch (AsnContentException)
        {
            return null;
        }
    }

    private static object? MapGet(Dictionary<object, object?> map, string key)
        => map.TryGetValue(key, out var value) ? value : null;

}
