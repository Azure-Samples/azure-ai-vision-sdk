using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace Azure.AI.Vision.Face.DeviceAttestation.Crypto;

/// <summary>Parsed validity result from <see cref="CertUtils.ValidateCertificate"/>.</summary>
internal sealed class CertificateValidationResult
{
    public bool Valid { get; init; }
    public string? Subject { get; init; }
    public string? Issuer { get; init; }
    public DateTimeOffset? ValidFrom { get; init; }
    public DateTimeOffset? ValidTo { get; init; }
}

/// <summary>Expiration info from <see cref="CertUtils.ValidateCertificateExpiration"/>.</summary>
internal sealed class CertificateExpirationInfo
{
    public bool IsValid { get; init; }
    public DateTimeOffset NotBefore { get; init; }
    public DateTimeOffset NotAfter { get; init; }
    public bool IsExpired { get; init; }
    public bool IsNotYetValid { get; init; }
}

/// <summary>Certificate parsing, thumbprinting, validation, and chain-link verification.</summary>
internal static class CertUtils
{
    public static byte[]? GetExtensionValue(byte[] certDer, string oid)
    {
        try
        {
            using var certificate = X509CertificateLoader.LoadCertificate(certDer);
            var matches = certificate.Extensions.Cast<X509Extension>()
                .Where(extension => extension.Oid?.Value == oid).ToArray();
            return matches.Length == 1 ? matches[0].RawData : null;
        }
        catch (CryptographicException)
        {
            return null;
        }
    }

    public static bool ValidateCertificatePath(IReadOnlyList<byte[]> chainDer, IEnumerable<string> pinnedCAs)
    {
        var certificates = new List<X509Certificate2>();
        try
        {
            if (chainDer.Count < 2 || !MatchesPinnedCA(chainDer[^1], pinnedCAs))
                return false;
            foreach (var der in chainDer)
                certificates.Add(X509CertificateLoader.LoadCertificate(der));
            var now = DateTime.UtcNow;
            if (certificates.Any(cert => now < cert.NotBefore.ToUniversalTime() || now > cert.NotAfter.ToUniversalTime()))
                return false;
            using var chain = new X509Chain();
            chain.ChainPolicy.TrustMode = X509ChainTrustMode.CustomRootTrust;
            chain.ChainPolicy.CustomTrustStore.Add(certificates[^1]);
            chain.ChainPolicy.ExtraStore.AddRange(certificates.Skip(1).ToArray());
            chain.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
            chain.ChainPolicy.DisableCertificateDownloads = true;
            chain.ChainPolicy.VerificationFlags = X509VerificationFlags.NoFlag;
            chain.ChainPolicy.VerificationTime = now;
            if (!chain.Build(certificates[0]) || chain.ChainElements.Count != certificates.Count)
                return false;
            return chain.ChainElements.Cast<X509ChainElement>()
                .Select((element, index) => element.Certificate.RawData.AsSpan().SequenceEqual(chainDer[index]))
                .All(matches => matches);
        }
        catch (CryptographicException)
        {
            return false;
        }
        finally
        {
            foreach (var certificate in certificates)
                certificate.Dispose();
        }
    }

    /// <summary>OID for the prime256v1 (secp256r1 / P-256) named curve.</summary>
    private const string P256Oid = "1.2.840.10045.3.1.7";

    /// <summary>
    /// Clock-skew tolerance applied to certificate validity checks. Kept small
    /// (5 minutes): the issuer backdates notBefore for real-world latency, so the
    /// server-side window only needs to absorb its own NTP drift.
    /// </summary>
    private static readonly TimeSpan ClockSkew = TimeSpan.FromMinutes(5);

    /// <summary>Convert a PEM certificate to DER bytes.</summary>
    public static byte[] PemToDer(string pemCert)
    {
        var fields = PemEncoding.Find(pemCert);
        if (!pemCert.AsSpan()[fields.Label].SequenceEqual("CERTIFICATE")
            || PemEncoding.TryFind(pemCert.AsSpan()[fields.Location.End..], out _))
            throw new ArgumentException("Expected a single CERTIFICATE PEM block", nameof(pemCert));
        return Convert.FromBase64String(pemCert[fields.Base64Data]);
    }

    /// <summary>Compute the SHA-256 thumbprint (lowercase hex) of a PEM certificate, or null.</summary>
    public static string? ComputeCertThumbprint(string pemCert)
    {
        try
        {
            byte[] der = PemToDer(pemCert);
            return Convert.ToHexString(SHA256.HashData(der)).ToLowerInvariant();
        }
        catch
        {
            return null;
        }
    }

    /// <summary>Parse a PEM certificate and check it is currently within its validity window.</summary>
    public static CertificateValidationResult? ValidateCertificate(string pemCert)
    {
        try
        {
            using var cert = X509Certificate2.CreateFromPem(pemCert);
            var now = DateTimeOffset.UtcNow;
            var validFrom = new DateTimeOffset(cert.NotBefore.ToUniversalTime());
            var validTo = new DateTimeOffset(cert.NotAfter.ToUniversalTime());

            if (now < validFrom || now > validTo)
            {
                return new CertificateValidationResult { Valid = false };
            }
            return new CertificateValidationResult
            {
                Valid = true,
                Subject = cert.Subject,
                Issuer = cert.Issuer,
                ValidFrom = validFrom,
                ValidTo = validTo,
            };
        }
        catch
        {
            return null;
        }
    }

    /// <summary>Check certificate expiration with a symmetric 5-minute clock-skew tolerance.</summary>
    public static CertificateExpirationInfo? ValidateCertificateExpiration(string pemCert)
    {
        try
        {
            using var cert = X509Certificate2.CreateFromPem(pemCert);
            var notBefore = new DateTimeOffset(cert.NotBefore.ToUniversalTime());
            var notAfter = new DateTimeOffset(cert.NotAfter.ToUniversalTime());
            var now = DateTimeOffset.UtcNow;

            bool isExpired = now - ClockSkew > notAfter;
            bool isNotYetValid = now + ClockSkew < notBefore;
            return new CertificateExpirationInfo
            {
                IsValid = !isExpired && !isNotYetValid,
                NotBefore = notBefore,
                NotAfter = notAfter,
                IsExpired = isExpired,
                IsNotYetValid = isNotYetValid,
            };
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Extract the EC public key from a certificate as SPKI PEM, validating that
    /// the curve is P-256. Returns null on error or non-P-256 keys.
    /// </summary>
    public static string? ExtractPublicKeyFromCert(string pemCert)
    {
        try
        {
            using var cert = X509Certificate2.CreateFromPem(pemCert);
            using var ecdsa = cert.GetECDsaPublicKey();
            if (ecdsa is null)
            {
                return null;
            }
            var parameters = ecdsa.ExportParameters(false);
            if (!IsP256(parameters.Curve))
            {
                return null;
            }
            return ecdsa.ExportSubjectPublicKeyInfoPem();
        }
        catch
        {
            return null;
        }
    }

    /// <summary>Return true if <paramref name="certDer"/> byte-matches any pinned CA (PEM).</summary>
    public static bool MatchesPinnedCA(byte[] certDer, IEnumerable<string> pinnedCAs)
    {
        try
        {
            foreach (string pinnedCA in pinnedCAs)
            {
                if (certDer.AsSpan().SequenceEqual(PemToDer(pinnedCA)))
                {
                    return true;
                }
            }
            return false;
        }
        catch
        {
            return false;
        }
    }

    private static bool IsP256(ECCurve curve)
        => curve.IsNamed
           && (curve.Oid?.Value == P256Oid
               || string.Equals(curve.Oid?.FriendlyName, "nistP256", StringComparison.OrdinalIgnoreCase)
               || string.Equals(curve.Oid?.FriendlyName, "ECDSA_P256", StringComparison.OrdinalIgnoreCase));
}
