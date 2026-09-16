using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;

/// <summary>Helpers for building self-signed P-256 certificates in tests.</summary>
internal static class TestCerts
{
    public static X509Certificate2 SelfSigned(string subject = "CN=test", X509Extension? extension = null)
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var req = new CertificateRequest(subject, key, HashAlgorithmName.SHA256);
        if (extension is not null)
        {
            req.CertificateExtensions.Add(extension);
        }
        return req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(1));
    }
}
