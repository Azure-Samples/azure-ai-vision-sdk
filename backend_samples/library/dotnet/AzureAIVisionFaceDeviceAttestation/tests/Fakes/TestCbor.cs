using System.Buffers.Binary;
using System.Formats.Cbor;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;

/// <summary>
/// CBOR encoder for tests — produces the App Attest assertion map
/// (<c>{ "signature": bytes, "authenticatorData": bytes }</c>) that the mobile
/// client would send, so the C# decoder + verifier can be exercised.
/// </summary>
internal static class TestCbor
{
    public static byte[] EncodeAssertion(byte[] signature, byte[] authenticatorData)
    {
        var writer = new CborWriter();
        writer.WriteStartMap(2);
        writer.WriteTextString("signature");
        writer.WriteByteString(signature);
        writer.WriteTextString("authenticatorData");
        writer.WriteByteString(authenticatorData);
        writer.WriteEndMap();
        return writer.Encode();
    }

    public static byte[] BuildAuthenticatorData(byte[] rpIdHash, byte flags, uint signCount)
    {
        var authData = new byte[37];
        Buffer.BlockCopy(rpIdHash, 0, authData, 0, 32);
        authData[32] = flags;
        BinaryPrimitives.WriteUInt32BigEndian(authData.AsSpan(33), signCount);
        return authData;
    }

}
