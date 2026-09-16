using System.Security.Cryptography;
using System.Text;

namespace Azure.AI.Vision.Face.DeviceAttestation.Crypto;

/// <summary>A P-256 EC key pair in PEM form (SPKI public key + PKCS8 private key).</summary>
internal sealed record EcKeyPair(string PublicKey, string PrivateKey);

/// <summary>
/// Elliptic-curve crypto primitives: P-256 key generation, ECDSA-SHA256
/// verification, and Tink-format ECIES (ECDH + HKDF-SHA256 + AES-256-GCM).
/// Faithfully mirrors the npm library's <c>crypto_utils.ts</c> so the same
/// mobile clients interoperate with either backend.
/// </summary>
internal static class CryptoUtils
{
    /// <summary>Generate a P-256 EC key pair, PEM-encoded (SPKI public, PKCS8 private).</summary>
    public static EcKeyPair? GenerateServerKeyPairEC()
    {
        try
        {
            using var ec = ECDsa.Create(ECCurve.NamedCurves.nistP256);
            return new EcKeyPair(ec.ExportSubjectPublicKeyInfoPem(), ec.ExportPkcs8PrivateKeyPem());
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Verify an ECDSA-SHA256 signature over <paramref name="data"/> (UTF-8).
    /// The signature is base64 and DER-encoded (Rfc3279 SEQUENCE{r,s}).
    /// </summary>
    public static bool VerifySignatureEC(string data, string signatureBase64, string publicKeyPem)
    {
        try
        {
            using var ecdsa = ECDsa.Create();
            ecdsa.ImportFromPem(publicKeyPem);
            byte[] signature = Base64Utils.Decode(signatureBase64);
            return ecdsa.VerifyData(
                Encoding.UTF8.GetBytes(data),
                signature,
                HashAlgorithmName.SHA256,
                DSASignatureFormat.Rfc3279DerSequence);
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// Encrypt <paramref name="data"/> using Tink-format ECIES for the recipient's
    /// P-256 public key. Serialization: <c>ephemeralPoint(65) || iv(12) ||
    /// ciphertext || authTag(16)</c>, base64-encoded.
    /// </summary>
    public static string? EncryptWithPublicKeyEC(string data, string publicKeyPem)
    {
        try
        {
            using var ephemeral = ECDiffieHellman.Create(ECCurve.NamedCurves.nistP256);
            byte[] ephemeralPoint = ToUncompressedPoint(ephemeral.PublicKey.ExportParameters().Q);

            using var recipient = ECDiffieHellman.Create();
            recipient.ImportFromPem(publicKeyPem);

            byte[] sharedSecret = ephemeral.DeriveRawSecretAgreement(recipient.PublicKey);
            byte[] aesKey = DeriveTinkAesKey(ephemeralPoint, sharedSecret);

            byte[] iv = RandomNumberGenerator.GetBytes(12);
            byte[] plaintext = Encoding.UTF8.GetBytes(data);
            byte[] ciphertext = new byte[plaintext.Length];
            byte[] authTag = new byte[16];
            using (var gcm = new AesGcm(aesKey, 16))
            {
                gcm.Encrypt(iv, plaintext, ciphertext, authTag);
            }

            return Convert.ToBase64String(Concat(ephemeralPoint, iv, ciphertext, authTag));
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Decrypt a Tink-format ECIES blob (<c>ephemeralPoint(65) || iv(12) ||
    /// ciphertext || authTag(16)</c>) with the recipient's P-256 private key.
    /// </summary>
    public static string? DecryptWithPrivateKeyEC(string tinkCiphertextBase64, string privateKeyPem)
    {
        try
        {
            byte[] blob = Base64Utils.Decode(tinkCiphertextBase64);
            if (blob.Length < 65 + 12 + 16)
            {
                return null;
            }

            byte[] ephemeralPoint = blob[..65];
            if (ephemeralPoint[0] != 0x04)
            {
                return null;
            }
            byte[] iv = blob[65..77];
            byte[] rest = blob[77..];
            byte[] authTag = rest[^16..];
            byte[] ciphertext = rest[..^16];

            var ephemeralParams = new ECParameters
            {
                Curve = ECCurve.NamedCurves.nistP256,
                Q = new ECPoint { X = ephemeralPoint[1..33], Y = ephemeralPoint[33..65] },
            };
            using var ephemeral = ECDiffieHellman.Create(ephemeralParams);

            using var recipient = ECDiffieHellman.Create();
            recipient.ImportFromPem(privateKeyPem);

            byte[] sharedSecret = recipient.DeriveRawSecretAgreement(ephemeral.PublicKey);
            byte[] aesKey = DeriveTinkAesKey(ephemeralPoint, sharedSecret);

            byte[] plaintext = new byte[ciphertext.Length];
            using (var gcm = new AesGcm(aesKey, 16))
            {
                gcm.Decrypt(iv, ciphertext, authTag, plaintext);
            }
            return Encoding.UTF8.GetString(plaintext);
        }
        catch
        {
            return null;
        }
    }

    // Tink ECIES KDF: HKDF-SHA256 over ikm = ephemeralPoint || sharedSecret, with
    // empty salt and info, producing a 32-byte AES key (Shoup's construction).
    private static byte[] DeriveTinkAesKey(byte[] ephemeralPoint, byte[] sharedSecret)
    {
        byte[] ikm = Concat(ephemeralPoint, sharedSecret);
        return HKDF.DeriveKey(HashAlgorithmName.SHA256, ikm, 32, Array.Empty<byte>(), Array.Empty<byte>());
    }

    private static byte[] ToUncompressedPoint(ECPoint q)
    {
        byte[] x = LeftPad(q.X!, 32);
        byte[] y = LeftPad(q.Y!, 32);
        byte[] point = new byte[65];
        point[0] = 0x04;
        Buffer.BlockCopy(x, 0, point, 1, 32);
        Buffer.BlockCopy(y, 0, point, 33, 32);
        return point;
    }

    private static byte[] LeftPad(byte[] value, int length)
    {
        if (value.Length == length)
        {
            return value;
        }
        if (value.Length > length)
        {
            throw new ArgumentException("Value longer than target length.", nameof(value));
        }
        byte[] result = new byte[length];
        Buffer.BlockCopy(value, 0, result, length - value.Length, value.Length);
        return result;
    }

    private static byte[] Concat(params byte[][] parts)
    {
        int total = 0;
        foreach (byte[] p in parts)
        {
            total += p.Length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        foreach (byte[] p in parts)
        {
            Buffer.BlockCopy(p, 0, result, offset, p.Length);
            offset += p.Length;
        }
        return result;
    }
}
