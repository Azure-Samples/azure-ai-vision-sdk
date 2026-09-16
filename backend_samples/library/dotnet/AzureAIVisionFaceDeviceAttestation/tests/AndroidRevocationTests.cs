using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class AndroidRevocationTests
{
    [Fact]
    public void CheckRevocation_RevokedWhenSerialListed()
    {
        using var cert = Fakes.TestCerts.SelfSigned();
        var serialHex = cert.SerialNumber.ToLowerInvariant().TrimStart('0');
        var list = new RevocationStatusList
        {
            Entries = new Dictionary<string, RevocationStatusEntry>
            {
                [serialHex] = new RevocationStatusEntry { Status = "REVOKED", Reason = "KEY_COMPROMISE" },
            },
        };

        var result = Revocation.CheckCertificateRevocation(cert.RawData, list);

        Assert.True(result.IsRevoked);
        Assert.Equal("REVOKED", result.Status);
        Assert.Equal("KEY_COMPROMISE", result.Reason);
    }

    [Theory]
    [InlineData("0F", "f")]
    [InlineData("10", "10")]
    [InlineData("0FFF", "fff")]
    [InlineData("80", "80")]
    public void CheckRevocation_UsesCanonicalSerial(string serialBytes, string canonicalSerial)
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=revocation-test", key, HashAlgorithmName.SHA256);
        using var cert = request.Create(request.SubjectName, X509SignatureGenerator.CreateForECDsa(key),
            DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1), Convert.FromHexString(serialBytes));
        var list = new RevocationStatusList
        {
            Entries = new Dictionary<string, RevocationStatusEntry>
            {
                [canonicalSerial] = new RevocationStatusEntry { Status = "REVOKED", Reason = "KEY_COMPROMISE" },
            },
        };

        Assert.True(Revocation.CheckCertificateRevocation(cert.RawData, list).IsRevoked);
    }

    [Fact]
    public void CheckRevocation_NotRevokedWhenSerialAbsent()
    {
        using var cert = Fakes.TestCerts.SelfSigned();
        var list = new RevocationStatusList { Entries = new Dictionary<string, RevocationStatusEntry>() };

        var result = Revocation.CheckCertificateRevocation(cert.RawData, list);

        Assert.False(result.IsRevoked);
    }

    [Fact]
    public void CheckRevocation_FailsClosedWhenListUnavailable()
    {
        using var cert = Fakes.TestCerts.SelfSigned();

        var result = Revocation.CheckCertificateRevocation(cert.RawData, null);

        Assert.True(result.IsRevoked);
        Assert.Equal("REVOCATION_CHECK_UNAVAILABLE", result.Reason);
    }
}
