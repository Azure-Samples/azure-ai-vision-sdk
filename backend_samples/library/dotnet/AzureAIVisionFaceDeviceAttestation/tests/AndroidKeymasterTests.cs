using System.Formats.Asn1;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class AndroidKeymasterTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void ExtractAttestationChallenge_IgnoresOidInsideUnrelatedExtension(bool includeRealExtension)
    {
        var challenge = RandomNumberGenerator.GetBytes(32);
        var oidWriter = new AsnWriter(AsnEncodingRules.DER);
        oidWriter.WriteObjectIdentifier(KeymasterExt.KeymasterExtOid);
        var decoy = new X509Extension(new Oid("1.2.3.4"),
            Concat(oidWriter.Encode(), Tlv(0x04, BuildKeyDescription(new byte[32]))), false);
        var real = new X509Extension(new Oid("1.3.6.1.4.1.11129.2.1.17"), BuildKeyDescription(challenge), true);
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=leaf", key, HashAlgorithmName.SHA256);
        request.CertificateExtensions.Add(decoy);
        if (includeRealExtension)
            request.CertificateExtensions.Add(real);
        using var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(1));

        var extracted = KeymasterExt.ExtractAttestationChallengeFromCert(cert.RawData);

        if (includeRealExtension)
            Assert.Equal(challenge, extracted);
        else
            Assert.Null(extracted);
    }

    [Fact]
    public void ExtractAttestationChallenge_ParsesKeymasterExtension()
    {
        var challenge = RandomNumberGenerator.GetBytes(32);
        var extension = new X509Extension(new Oid("1.3.6.1.4.1.11129.2.1.17"), BuildKeyDescription(challenge), critical: false);
        using var cert = Fakes.TestCerts.SelfSigned("CN=leaf", extension);

        var extracted = KeymasterExt.ExtractAttestationChallengeFromCert(cert.RawData);

        Assert.NotNull(extracted);
        Assert.Equal(challenge, extracted);
        var description = KeymasterExt.ParseKeyDescription(cert.RawData);
        Assert.NotNull(description);
        Assert.Equal(1, description.AttestationVersion);
        Assert.Equal(1, description.AttestationSecurityLevel);
        Assert.Equal(2, description.KeyMintVersion);
        Assert.Equal(1, description.KeyMintSecurityLevel);
    }

    [Fact]
    public void ExtractAttestationChallenge_ReturnsNullWhenExtensionMissing()
    {
        using var cert = Fakes.TestCerts.SelfSigned("CN=leaf");
        Assert.Null(KeymasterExt.ExtractAttestationChallengeFromCert(cert.RawData));
    }

    [Theory]
    [InlineData(0, false)]
    [InlineData(1, true)]
    [InlineData(2, true)]
    [InlineData(3, false)]
    public void HardwareAttestationSecurityLevel_AcceptsOnlyTeeOrStrongBox(byte securityLevel, bool expected)
    {
        var extension = new X509Extension(
            new Oid(KeymasterExt.KeymasterExtOid),
            BuildKeyDescription(new byte[32], securityLevel),
            critical: false);
        using var cert = Fakes.TestCerts.SelfSigned("CN=leaf", extension);

        var description = KeymasterExt.ParseKeyDescription(cert.RawData);

        Assert.Equal(expected, KeymasterExt.IsHardwareAttestationSecurityLevel(description));
    }

    // KeyDescription ::= SEQUENCE { attestationVersion INTEGER, attestationSecurityLevel
    //   ENUMERATED, keyMintVersion INTEGER, keyMintSecurityLevel ENUMERATED,
    //   attestationChallenge OCTET STRING, ... }
    private static byte[] BuildKeyDescription(byte[] challenge, byte attestationSecurityLevel = 0x01)
    {
        var body = Concat(
            Tlv(0x02, new byte[] { 0x01 }),
            Tlv(0x0a, new byte[] { attestationSecurityLevel }),
            Tlv(0x02, new byte[] { 0x02 }),
            Tlv(0x0a, new byte[] { 0x01 }),
            Tlv(0x04, challenge));
        return Tlv(0x30, body);
    }

    private static byte[] Tlv(byte tag, byte[] content)
    {
        var result = new byte[2 + content.Length];
        result[0] = tag;
        result[1] = (byte)content.Length;
        content.CopyTo(result, 2);
        return result;
    }

    private static byte[] Concat(params byte[][] parts)
    {
        var result = new List<byte>();
        foreach (var part in parts)
        {
            result.AddRange(part);
        }
        return result.ToArray();
    }
}
