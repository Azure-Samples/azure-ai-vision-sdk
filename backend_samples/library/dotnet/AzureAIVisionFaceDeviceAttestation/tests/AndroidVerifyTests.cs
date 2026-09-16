using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Services;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class AndroidVerifyTests
{
    private static AttestationConfig Config()
        => new() { AndroidPackageName = "com.example.app", GoogleServiceAccountJson = "{}" };

    private static AttestationMessageData Message(string leafPem)
        => new() { ClientId = "c", System = "android", ChallengeHash = new string('a', 64), PublicCert = leafPem };

    private static Task<AuthVerificationResult> Verify(string attestJson, string leafPem)
        => AndroidVerification.VerifyAsync(Config(), NullAttestationLogger.Instance, Message(leafPem), attestJson);

    [Fact]
    public async Task Verify_FailsOnMalformedJson()
    {
        using var cert = Fakes.TestCerts.SelfSigned();
        var result = await Verify("not json", cert.ExportCertificatePem());
        Assert.False(result.Verified);
        Assert.Contains("failed to parse", result.Message);
    }

    [Fact]
    public async Task Verify_FailsOnMissingChain()
    {
        using var cert = Fakes.TestCerts.SelfSigned();
        var result = await Verify("{}", cert.ExportCertificatePem());
        Assert.False(result.Verified);
        Assert.Contains("missing or invalid certificateChain", result.Message);
    }

    [Fact]
    public async Task Verify_FailsOnEmptyChain()
    {
        using var cert = Fakes.TestCerts.SelfSigned();
        var attestJson = JsonSerializer.Serialize(new { token = "x", certificateChain = Array.Empty<string>() });
        var result = await Verify(attestJson, cert.ExportCertificatePem());
        Assert.False(result.Verified);
        Assert.Contains("empty certificateChain", result.Message);
    }

    [Fact]
    public async Task Verify_RejectsNonGoogleRoot()
    {
        // Leaf == chain[0] (a self-signed cert): passes leaf-binding + validity,
        // then fails at the pinned-root check.
        using var cert = Fakes.TestCerts.SelfSigned();
        var attestJson = JsonSerializer.Serialize(new
        {
            token = "x",
            certificateChain = new[] { Convert.ToBase64String(cert.RawData) },
        });
        var result = await Verify(attestJson, cert.ExportCertificatePem());
        Assert.False(result.Verified);
        Assert.Contains("root CA does not match any pinned CA", result.Message);
    }

    [Fact]
    public async Task Verify_RejectsLeafNotBoundToChain()
    {
        using var chainCert = Fakes.TestCerts.SelfSigned("CN=chain0");
        using var leafCert = Fakes.TestCerts.SelfSigned("CN=leaf");
        var attestJson = JsonSerializer.Serialize(new
        {
            token = "x",
            certificateChain = new[] { Convert.ToBase64String(chainCert.RawData) },
        });
        var result = await Verify(attestJson, leafCert.ExportCertificatePem());
        Assert.False(result.Verified);
        Assert.Contains("must match certificateChain[0]", result.Message);
    }

    [Fact]
    public async Task Verify_RejectsPinnedRootWithoutLeaf()
    {
        var rootPem = GoogleRoots.GoogleHardwareAttestationRootCAs[0];
        var attestJson = JsonSerializer.Serialize(new
        {
            token = "x",
            certificateChain = new[] { Convert.ToBase64String(CertUtils.PemToDer(rootPem)) },
        });
        var result = await Verify(attestJson, rootPem);
        Assert.False(result.Verified);
        Assert.Equal("Certificate path validation failed", result.Message);
    }

    [Fact]
    public async Task Verify_RejectsSeparateCertificateSignedByChainLeaf()
    {
        using var chainKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var chainRequest = new CertificateRequest("CN=chain0", chainKey, HashAlgorithmName.SHA256);
        using var chainCert = chainRequest.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddMinutes(-5),
            DateTimeOffset.UtcNow.AddDays(1));

        using var authKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var authRequest = new CertificateRequest("CN=auth", authKey, HashAlgorithmName.SHA256);
        var signer = X509SignatureGenerator.CreateForECDsa(chainKey);
        using var authCert = authRequest.Create(
            chainCert.SubjectName,
            signer,
            DateTimeOffset.UtcNow.AddMinutes(-5),
            DateTimeOffset.UtcNow.AddDays(1),
            new byte[] { 1, 2, 3, 4 });

        var encoded = new System.Formats.Asn1.AsnReader(authCert.RawData, System.Formats.Asn1.AsnEncodingRules.DER).ReadSequence();
        var tbs = encoded.ReadEncodedValue();
        encoded.ReadEncodedValue();
        Assert.True(chainKey.VerifyData(tbs.Span, encoded.ReadBitString(out _), HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence));

        var attestJson = JsonSerializer.Serialize(new
        {
            token = "x",
            certificateChain = new[] { Convert.ToBase64String(chainCert.RawData) },
        });
        var result = await Verify(attestJson, authCert.ExportCertificatePem());

        Assert.False(result.Verified);
        Assert.Contains("must match certificateChain[0]", result.Message);
    }
}
