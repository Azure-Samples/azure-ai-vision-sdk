using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;

/// <summary>
/// Simulates the mobile client's key material: an auth key pair (for signatures)
/// and an encryption key pair (for ECIES), each wrapped in a self-signed P-256
/// certificate, mirroring what the device sends to the backend.
/// </summary>
internal sealed class TestAttestationClient
{
    private readonly ECDsa _authKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
    private readonly ECDsa _encKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);

    public string AuthCertPem { get; }
    public string EncCertPem { get; }
    public string AuthThumbprint { get; }
    public string EncPrivateKeyPem { get; }

    public TestAttestationClient()
    {
        AuthCertPem = SelfSigned(_authKey, "CN=auth");
        EncCertPem = SelfSigned(_encKey, "CN=enc");
        AuthThumbprint = CertUtils.ComputeCertThumbprint(AuthCertPem)!;
        EncPrivateKeyPem = _encKey.ExportPkcs8PrivateKeyPem();
    }

    /// <summary>Base64 DER ECDSA-SHA256 signature over <paramref name="data"/> using the auth key.</summary>
    public string Sign(string data)
        => Convert.ToBase64String(_authKey.SignData(Encoding.UTF8.GetBytes(data), HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence));

    private static string SelfSigned(ECDsa key, string subject)
    {
        var req = new CertificateRequest(subject, key, HashAlgorithmName.SHA256);
        using var cert = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(1));
        return cert.ExportCertificatePem();
    }
}
