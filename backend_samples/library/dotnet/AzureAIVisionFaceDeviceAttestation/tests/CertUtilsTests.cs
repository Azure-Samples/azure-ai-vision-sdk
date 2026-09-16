using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Android;
using Azure.AI.Vision.Face.DeviceAttestation.Ios;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class CertUtilsTests
{
    private static readonly DateTimeOffset NotBefore = DateTimeOffset.UtcNow.AddMinutes(-5);
    private static readonly DateTimeOffset NotAfter = DateTimeOffset.UtcNow.AddDays(1);

    [Theory]
    [InlineData("1.2.840.113635.100.8.2", "3024a1220420")]
    [InlineData("1.3.6.1.4.1.11129.2.1.17", "302e0201010a01010201020a01010420")]
    public void ExtractExtension_RejectsMalformedPayloads(string oid, string prefix)
    {
        var payload = Convert.FromHexString(prefix + new string('a', 64));
        var wrongTag = (byte[])payload.Clone();
        wrongTag[2] = 0x05;
        var nestedOrNegative = oid == AppAttestConstants.NonceOid
            ? Convert.FromHexString("3026a1240420" + new string('a', 64) + "0500") : (byte[])payload.Clone();
        if (oid == KeymasterExt.KeymasterExtOid)
            nestedOrNegative[4] = 0xff;
        Func<byte[], byte[]?> extract = oid == AppAttestConstants.NonceOid
            ? AppAttestParsers.ExtractNonceFromCredCert : KeymasterExt.ExtractAttestationChallengeFromCert;
        foreach (var malformed in new[] { Array.Empty<byte>(), new byte[] { 0x30 }, payload[..^1],
            wrongTag, payload.Concat(new byte[] { 0x05, 0x00 }).ToArray(), nestedOrNegative })
        {
            using var cert = Fakes.TestCerts.SelfSigned("CN=malformed", new X509Extension(oid, malformed, false));
            Assert.Null(extract(cert.RawData));
        }
    }

    [Theory]
    [InlineData("1.2.840.113635.100.8.2", false)]
    [InlineData("1.2.840.113635.100.8.2", true)]
    [InlineData("1.3.6.1.4.1.11129.2.1.17", false)]
    [InlineData("1.3.6.1.4.1.11129.2.1.17", true)]
    public void GetExtensionValue_ReturnsOnlyActualExtension(string oid, bool critical)
    {
        var payload = Convert.FromHexString("3024a1220420" + new string('a', 64));
        var decoy = Convert.FromHexString("06092a864886f7636408020426" + Convert.ToHexString(payload));
        using var missing = Fakes.TestCerts.SelfSigned("CN=decoy", new X509Extension("1.2.3.4", decoy, false));
        Assert.Null(CertUtils.GetExtensionValue(missing.RawData, oid));
        Assert.Null(AppAttestParsers.ExtractNonceFromCredCert(missing.RawData));
        Assert.Null(CertUtils.GetExtensionValue(new byte[] { 0x30, 0x00 }, oid));

        using var cert = Fakes.TestCerts.SelfSigned("CN=real", new X509Extension(oid, payload, critical));
        Assert.Equal(payload, CertUtils.GetExtensionValue(cert.RawData, oid));
        if (oid == "1.2.840.113635.100.8.2")
            Assert.Equal(payload[6..], AppAttestParsers.ExtractNonceFromCredCert(cert.RawData));
    }

    [Fact]
    public void ComputeCertThumbprint_MatchesSha256OfDer()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var req = new CertificateRequest("CN=Thumbprint Test", key, HashAlgorithmName.SHA256);
        using var cert = req.CreateSelfSigned(NotBefore, NotAfter);

        var expected = Convert.ToHexString(SHA256.HashData(cert.RawData)).ToLowerInvariant();
        var actual = CertUtils.ComputeCertThumbprint(cert.ExportCertificatePem());

        Assert.Equal(expected, actual);
    }

    [Fact]
    public void PemToDer_PreservesBytesAndRejectsInvalidBlocks()
    {
        using var cert = Fakes.TestCerts.SelfSigned("CN=PEM test");
        var pem = cert.ExportCertificatePem().Replace("\r\n", "\n");
        Assert.Equal(cert.RawData, CertUtils.PemToDer(pem));
        Assert.Equal(cert.RawData, CertUtils.PemToDer(pem.Replace("\n", "\r\n")));
        foreach (var malformed in new[] { "AA==", "not PEM", pem + "\n" + pem,
            pem.Replace("CERTIFICATE", "PUBLIC KEY"), pem.Replace("END CERTIFICATE", "END PUBLIC KEY"),
            "-----BEGIN CERTIFICATE-----\n!\n-----END CERTIFICATE-----" })
        {
            Assert.ThrowsAny<ArgumentException>(() => CertUtils.PemToDer(malformed));
            Assert.Null(CertUtils.ComputeCertThumbprint(malformed));
        }
    }

    [Theory]
    [InlineData("valid", true)]
    [InlineData("untrusted", false)]
    [InlineData("non-ca", false)]
    [InlineData("key-usage", false)]
    [InlineData("path-length", false)]
    [InlineData("expired", false)]
    [InlineData("future", false)]
    [InlineData("expired-root", false)]
    [InlineData("critical-extension", false)]
    [InlineData("signature", false)]
    [InlineData("missing", false)]
    [InlineData("reordered", false)]
    [InlineData("extra", false)]
    public void ValidateCertificatePath_EnforcesPinnedOrderedPath(string scenario, bool expected)
    {
        using var rootKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var rootRequest = new CertificateRequest("CN=Path Root", rootKey, HashAlgorithmName.SHA256);
        rootRequest.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, true, scenario == "path-length" ? 0 : 1, true));
        rootRequest.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.KeyCertSign, true));
        using var root = rootRequest.CreateSelfSigned(NotBefore.AddDays(-10), scenario == "expired-root" ? NotBefore.AddDays(-1) : NotAfter);
        using var issuerKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var issuerRequest = new CertificateRequest("CN=Path Issuer", issuerKey, HashAlgorithmName.SHA256);
        issuerRequest.CertificateExtensions.Add(new X509BasicConstraintsExtension(scenario != "non-ca", false, 0, true));
        issuerRequest.CertificateExtensions.Add(new X509KeyUsageExtension(
            scenario == "key-usage" ? X509KeyUsageFlags.DigitalSignature : X509KeyUsageFlags.KeyCertSign, true));
        using var issuer = issuerRequest.Create(root.SubjectName, X509SignatureGenerator.CreateForECDsa(rootKey), NotBefore, NotAfter, new byte[] { 2 });
        using var leafKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var leafRequest = new CertificateRequest("CN=Path Leaf", leafKey, HashAlgorithmName.SHA256);
        if (scenario == "critical-extension")
            leafRequest.CertificateExtensions.Add(new X509Extension("1.2.3.4", new byte[] { 5, 0 }, true));
        using var leaf = leafRequest.Create(issuer.SubjectName, X509SignatureGenerator.CreateForECDsa(issuerKey),
            scenario == "future" ? NotAfter : NotBefore.AddDays(-2),
            scenario == "expired" ? NotBefore : NotAfter.AddDays(1), new byte[] { 3 });
        var chain = new List<byte[]> { leaf.RawData, issuer.RawData, root.RawData };
        if (scenario == "signature") chain[0][^1] ^= 1;
        if (scenario == "missing") chain.RemoveAt(1);
        if (scenario == "reordered") (chain[0], chain[1]) = (chain[1], chain[0]);
        if (scenario == "extra") chain.Insert(1, root.RawData);
        var roots = scenario == "untrusted" ? Array.Empty<string>() : new[] { root.ExportCertificatePem() };
        Assert.Equal(expected, CertUtils.ValidateCertificatePath(chain, roots));
        Assert.False(CertUtils.ValidateCertificatePath(new[] { root.RawData }, roots));
        Assert.False(CertUtils.ValidateCertificatePath(new[] { new byte[] { 0x30 }, root.RawData }, roots));
    }

    [Fact]
    public void ComputeCertThumbprint_ReturnsNullForNonPem()
        => Assert.Null(CertUtils.ComputeCertThumbprint("not a certificate"));

    [Fact]
    public void ExtractPublicKeyFromCert_ReturnsP256SpkiThatVerifies()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var req = new CertificateRequest("CN=Extract Test", key, HashAlgorithmName.SHA256);
        using var cert = req.CreateSelfSigned(NotBefore, NotAfter);

        var pubPem = CertUtils.ExtractPublicKeyFromCert(cert.ExportCertificatePem());
        Assert.NotNull(pubPem);

        const string data = "sign-me";
        var sig = key.SignData(System.Text.Encoding.UTF8.GetBytes(data), HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);
        Assert.True(CryptoUtils.VerifySignatureEC(data, Convert.ToBase64String(sig), pubPem!));
    }

    [Fact]
    public void ValidateCertificatePath_EcdsaIssuer()
    {
        using var caKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var caReq = new CertificateRequest("CN=EC CA", caKey, HashAlgorithmName.SHA256);
        caReq.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
        using var ca = caReq.CreateSelfSigned(NotBefore, NotAfter);

        using var leafKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var leafReq = new CertificateRequest("CN=EC Leaf", leafKey, HashAlgorithmName.SHA256);
        using var leaf = leafReq.Create(ca, NotBefore, NotAfter, new byte[] { 1, 2, 3, 4 });

        Assert.True(CertUtils.ValidateCertificatePath(new[] { leaf.RawData, ca.RawData }, new[] { ca.ExportCertificatePem() }));

        using var otherCaKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var otherReq = new CertificateRequest("CN=Other CA", otherCaKey, HashAlgorithmName.SHA256);
        otherReq.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
        using var otherCa = otherReq.CreateSelfSigned(NotBefore, NotAfter);
        Assert.False(CertUtils.ValidateCertificatePath(new[] { leaf.RawData, otherCa.RawData }, new[] { otherCa.ExportCertificatePem() }));
    }

    [Fact]
    public void ValidateCertificatePath_RsaIssuer()
    {
        using var rsaCaKey = RSA.Create(2048);
        var rsaCaReq = new CertificateRequest("CN=RSA CA", rsaCaKey, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        rsaCaReq.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
        using var rsaCa = rsaCaReq.CreateSelfSigned(NotBefore, NotAfter);

        using var leafKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var leafReq = new CertificateRequest("CN=EC Leaf", leafKey, HashAlgorithmName.SHA256);
        var generator = X509SignatureGenerator.CreateForRSA(rsaCaKey, RSASignaturePadding.Pkcs1);
        using var leaf = leafReq.Create(rsaCa.SubjectName, generator, NotBefore, NotAfter, new byte[] { 5, 6, 7, 8 });

        Assert.True(CertUtils.ValidateCertificatePath(new[] { leaf.RawData, rsaCa.RawData }, new[] { rsaCa.ExportCertificatePem() }));
    }

    [Fact]
    public void MatchesPinnedCA_ByteComparesDer()
    {
        using var caKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var caReq = new CertificateRequest("CN=Pinned CA", caKey, HashAlgorithmName.SHA256);
        using var ca = caReq.CreateSelfSigned(NotBefore, NotAfter);

        Assert.True(CertUtils.MatchesPinnedCA(ca.RawData, new[] { ca.ExportCertificatePem() }));

        using var otherKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var otherReq = new CertificateRequest("CN=Other", otherKey, HashAlgorithmName.SHA256);
        using var other = otherReq.CreateSelfSigned(NotBefore, NotAfter);
        Assert.False(CertUtils.MatchesPinnedCA(ca.RawData, new[] { other.ExportCertificatePem() }));
    }

    [Fact]
    public void ValidateCertificateExpiration_FlagsExpiredAndNotYetValid()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);

        var expiredReq = new CertificateRequest("CN=Expired", key, HashAlgorithmName.SHA256);
        using var expired = expiredReq.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-10), DateTimeOffset.UtcNow.AddDays(-1));
        var expiredInfo = CertUtils.ValidateCertificateExpiration(expired.ExportCertificatePem());
        Assert.NotNull(expiredInfo);
        Assert.True(expiredInfo!.IsExpired);
        Assert.False(expiredInfo.IsValid);

        var futureReq = new CertificateRequest("CN=Future", key, HashAlgorithmName.SHA256);
        using var future = futureReq.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(1), DateTimeOffset.UtcNow.AddDays(10));
        var futureInfo = CertUtils.ValidateCertificateExpiration(future.ExportCertificatePem());
        Assert.NotNull(futureInfo);
        Assert.True(futureInfo!.IsNotYetValid);
    }
}
