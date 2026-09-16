using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Ios;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Services;
using Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class IosAssertionTests
{
    private const string AppId = "ABCDE12345.com.example.app";

    private static string RpIdHashHex()
        => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(AppId))).ToLowerInvariant();

    private static byte[] RpIdHash() => SHA256.HashData(Encoding.UTF8.GetBytes(AppId));

    private static byte[] Sha256Concat(byte[] a, byte[] b)
    {
        using var ih = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        ih.AppendData(a);
        ih.AppendData(b);
        return ih.GetHashAndReset();
    }

    private static (string CredCertPem, string AssertionB64) BuildAssertion(ECDsa credKey, byte[] blob, uint signCount, byte[]? rpIdHashOverride = null)
    {
        var req = new CertificateRequest("CN=cred", credKey, HashAlgorithmName.SHA256);
        using var cert = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(1));
        var authData = TestCbor.BuildAuthenticatorData(rpIdHashOverride ?? RpIdHash(), 0, signCount);
        var nonce = Sha256Concat(authData, SHA256.HashData(blob));
        var signature = credKey.SignData(nonce, HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);
        var assertionB64 = Convert.ToBase64String(TestCbor.EncodeAssertion(signature, authData));
        return (cert.ExportCertificatePem(), assertionB64);
    }

    [Fact]
    public void OngoingAssertion_HappyPath()
    {
        using var credKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var blob = Encoding.UTF8.GetBytes("call-blob-bytes");
        var (credCertPem, assertionB64) = BuildAssertion(credKey, blob, signCount: 5);

        var result = IosVerifier.VerifyIosOngoingAssertion(credCertPem, blob, assertionB64, RpIdHashHex(), lastSignCount: 0);

        Assert.True(result.Ok);
        Assert.Equal(5, result.SignCount);
    }

    [Fact]
    public void OngoingAssertion_RejectsNonIncrementingSignCount()
    {
        using var credKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var blob = Encoding.UTF8.GetBytes("call-blob-bytes");
        var (credCertPem, assertionB64) = BuildAssertion(credKey, blob, signCount: 5);

        var result = IosVerifier.VerifyIosOngoingAssertion(credCertPem, blob, assertionB64, RpIdHashHex(), lastSignCount: 5);

        Assert.False(result.Ok);
        Assert.Equal("ASSERTION_SIGNCOUNT_NOT_INCREMENTED", result.Reason);
    }

    [Fact]
    public void OngoingAssertion_RejectsRpIdMismatch()
    {
        using var credKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var blob = Encoding.UTF8.GetBytes("call-blob-bytes");
        var (credCertPem, assertionB64) = BuildAssertion(credKey, blob, signCount: 5, rpIdHashOverride: new byte[32]);

        var result = IosVerifier.VerifyIosOngoingAssertion(credCertPem, blob, assertionB64, RpIdHashHex(), lastSignCount: 0);

        Assert.False(result.Ok);
        Assert.Equal("ASSERTION_RPID_MISMATCH", result.Reason);
    }

    [Fact]
    public void OngoingAssertion_RejectsTamperedBlob()
    {
        using var credKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var blob = Encoding.UTF8.GetBytes("call-blob-bytes");
        var (credCertPem, assertionB64) = BuildAssertion(credKey, blob, signCount: 5);

        var result = IosVerifier.VerifyIosOngoingAssertion(credCertPem, Encoding.UTF8.GetBytes("different-blob"), assertionB64, RpIdHashHex(), lastSignCount: 0);

        Assert.False(result.Ok);
        Assert.Equal("ASSERTION_SIGNATURE_INVALID", result.Reason);
    }

    [Fact]
    public void GetExpectedIosRpIdHash_IsSha256OfAppId()
        => Assert.Equal(RpIdHashHex(), IosVerifier.GetExpectedIosRpIdHash(new AttestationConfig { IosAppId = AppId }));

    [Theory]
    [InlineData("not json", "failed to parse")]
    [InlineData("{}", "missing attestation")]
    public void Verify_FailsOnMalformedEnvelope(string attestJson, string expectedFragment)
    {
        var config = new AttestationConfig { IosAppId = AppId };
        var messageData = new AttestationMessageData { ClientId = "c", System = "ios", ChallengeHash = new string('a', 64), PublicCert = "" };

        var result = AppAttestVerification.Verify(config, NullAttestationLogger.Instance, messageData, attestJson);

        Assert.False(result.Verified);
        Assert.Contains(expectedFragment, result.Message);
    }
}
