using System.Collections.Generic;
using System.Buffers.Binary;
using System.Reflection;
using Azure.AI.Vision.Face.DeviceAttestation.Ios;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class IosCborTests
{
    [Fact]
    public void AssertionHeader_PreservesUnsignedCountersAndFieldBoundaries()
    {
        foreach (uint counter in new uint[] { 0, 0x01020304, 0x7fffffff, 0x80000000, 0xffffffff })
        {
            var authData = new byte[38];
            Array.Fill(authData, (byte)0x42, 0, 32);
            authData[32] = 0xff;
            BinaryPrimitives.WriteUInt32BigEndian(authData.AsSpan(33), counter);
            authData[37] = 0xee;
            var parsed = AppAttestParsers.ParseAssertionAuthData(authData);
            Assert.Equal(authData[..32], parsed.RpIdHash);
            Assert.Equal(255, parsed.Flags);
            Assert.Equal((long)counter, parsed.SignCount);
        }
        Assert.Equal("authenticatorData is 36 bytes, < 37",
            Assert.Throws<FormatException>(() => AppAttestParsers.ParseAssertionAuthData(new byte[36])).Message);
    }

    [Fact]
    public void AttestedCredentialLayout_PreservesLengthsAndTruncationErrors()
    {
        var parser = typeof(AppAttestVerification).GetMethod("ParseAttestAuthData", BindingFlags.NonPublic | BindingFlags.Static)!;
        foreach (int length in new[] { 0, 1, 255, 256, 32768, 65535 })
        {
            var authData = new byte[55 + length + 1];
            Array.Fill(authData, (byte)0x42, 0, 32);
            Array.Fill(authData, (byte)0xff, 32, 5);
            Array.Fill(authData, (byte)0x61, 37, 16);
            BinaryPrimitives.WriteUInt16BigEndian(authData.AsSpan(53), (ushort)length);
            Array.Fill(authData, (byte)0xab, 55, length);
            authData[55 + length] = 0xee;
            var parsed = ((byte[] RpIdHash, int Flags, long SignCount, byte[] Aaguid, int CredIdLen, byte[] CredentialId))
                parser.Invoke(null, new object[] { authData })!;
            Assert.Equal(authData[..32], parsed.RpIdHash);
            Assert.Equal(255, parsed.Flags);
            Assert.Equal(4294967295L, parsed.SignCount);
            Assert.Equal(authData[37..53], parsed.Aaguid);
            Assert.Equal(length, parsed.CredIdLen);
            Assert.Equal(authData[55..(55 + length)], parsed.CredentialId);
        }
        foreach (int length in new[] { 0, 36, 37, 54, 55 })
        {
            var authData = new byte[length];
            if (length == 55) authData[54] = 1;
            var error = Assert.Throws<TargetInvocationException>(() => parser.Invoke(null, new object[] { authData }));
            string expected = length < 37 ? $"authData is {length} bytes, < 37"
                : length < 55 ? "authData missing attested credential data" : "authData truncated within credentialId";
            Assert.Equal(expected, error.InnerException!.Message);
        }
    }

    [Fact]
    public void Decode_LibraryEncodingWorksWithAssertionParser()
    {
        var signature = new byte[] { 1, 2, 3 };
        var authData = new byte[37];
        var encoded = Fakes.TestCbor.EncodeAssertion(signature, authData);
        var assertion = AppAttestParsers.ParseAppAttestAssertion(Convert.ToBase64String(encoded));
        Assert.Equal(signature, assertion.Signature);
        Assert.Equal(authData, assertion.AuthenticatorData);
    }

    [Fact]
    public void Decode_UnsignedInts()
    {
        Assert.Equal(0L, Cbor.Decode(new byte[] { 0x00 }).Value);
        Assert.Equal(23L, Cbor.Decode(new byte[] { 0x17 }).Value);
        Assert.Equal(42L, Cbor.Decode(new byte[] { 0x18, 0x2a }).Value);
        Assert.Equal(256L, Cbor.Decode(new byte[] { 0x19, 0x01, 0x00 }).Value);
    }

    [Fact]
    public void Decode_ByteAndTextStrings()
    {
        Assert.Equal(new byte[] { 0x01, 0x02, 0x03 }, (byte[])Cbor.Decode(new byte[] { 0x43, 0x01, 0x02, 0x03 }).Value!);
        Assert.Equal("abc", Cbor.Decode(new byte[] { 0x63, 0x61, 0x62, 0x63 }).Value);
    }

    [Fact]
    public void Decode_ArrayAndMap()
    {
        var arr = Assert.IsType<List<object?>>(Cbor.Decode(new byte[] { 0x82, 0x01, 0x02 }).Value);
        Assert.Equal(new object?[] { 1L, 2L }, arr);

        var map = Assert.IsType<Dictionary<object, object?>>(Cbor.Decode(new byte[] { 0xA1, 0x61, 0x61, 0x01 }).Value);
        Assert.Equal(1L, map["a"]);
    }

    [Fact]
    public void Decode_RejectsUnsupportedMajorType()
        => Assert.ThrowsAny<Exception>(() => Cbor.Decode(new byte[] { 0xE0 }));

    [Theory]
    [InlineData("")]
    [InlineData("18")]
    [InlineData("430102")]
    [InlineData("636162")]
    [InlineData("8201")]
    [InlineData("a16161")]
    [InlineData("9f01ff")]
    [InlineData("bf616101ff")]
    [InlineData("5f4101ff")]
    [InlineData("7f6161ff")]
    [InlineData("c001")]
    [InlineData("f5")]
    [InlineData("f6")]
    [InlineData("f93c00")]
    [InlineData("a18001")]
    [InlineData("1bffffffffffffffff")]
    public void Decode_RejectsMalformedOrUnsupportedValues(string hex)
        => Assert.ThrowsAny<Exception>(() => Cbor.Decode(Convert.FromHexString(hex)));

    [Fact]
    public void Decode_PreservesOffsetsAndSignedIntegers()
    {
        var decoded = Cbor.Decode(Convert.FromHexString("002900"), 1);
        Assert.Equal(-10L, decoded.Value);
        Assert.Equal(2, decoded.NextPos);
        Assert.Equal(4294967296L, Cbor.Decode(Convert.FromHexString("1b0000000100000000")).Value);
    }

    [Fact]
    public void Decode_LimitsNesting()
    {
        Cbor.Decode(Enumerable.Repeat((byte)0x81, 16).Append((byte)0).ToArray());
        Assert.ThrowsAny<Exception>(() => Cbor.Decode(Enumerable.Repeat((byte)0x81, 17).Append((byte)0).ToArray()));
    }
}
