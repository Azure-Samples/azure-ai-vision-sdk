using System.Diagnostics;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

/// <summary>
/// Cross-language crypto parity tests. Vectors are generated at test time by the
/// npm library's exact primitives (tests/Interop/gen.js, pure Node `crypto`), so
/// no key material is checked in. The cross-language tests skip when Node is not
/// on PATH; the C#-only round-trip tests always run.
/// </summary>
public class CryptoInteropTests
{
    private sealed record NodeVector(
        string AuthPublicKey,
        string SignedData,
        string Signature,
        string RecipientPublicKey,
        string RecipientPrivateKey,
        string Plaintext,
        string TinkCiphertext);

    [Fact]
    public void VerifySignatureEC_AcceptsNodeSignature()
    {
        var vector = GenerateNodeVector();
        if (vector is null)
        {
            return; // Node unavailable
        }
        Assert.True(CryptoUtils.VerifySignatureEC(vector.SignedData, vector.Signature, vector.AuthPublicKey));
        Assert.False(CryptoUtils.VerifySignatureEC(vector.SignedData + "x", vector.Signature, vector.AuthPublicKey));
    }

    [Fact]
    public void VerifySignatureEC_AcceptsUrlSafeBase64WithoutPadding()
    {
        using var key = System.Security.Cryptography.ECDsa.Create(
            System.Security.Cryptography.ECCurve.NamedCurves.nistP256);
        const string data = "android-url-safe-signature";
        var signature = key.SignData(
            Encoding.UTF8.GetBytes(data),
            System.Security.Cryptography.HashAlgorithmName.SHA256,
            System.Security.Cryptography.DSASignatureFormat.Rfc3279DerSequence);
        var base64Url = Convert.ToBase64String(signature)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');

        Assert.True(CryptoUtils.VerifySignatureEC(data, base64Url, key.ExportSubjectPublicKeyInfoPem()));
    }

    [Fact]
    public void DecryptWithPrivateKeyEC_DecryptsNodeCiphertext()
    {
        var vector = GenerateNodeVector();
        if (vector is null)
        {
            return; // Node unavailable
        }
        Assert.Equal(vector.Plaintext, CryptoUtils.DecryptWithPrivateKeyEC(vector.TinkCiphertext, vector.RecipientPrivateKey));
    }

    [Fact]
    public void Ecies_RoundTrips()
    {
        var pair = CryptoUtils.GenerateServerKeyPairEC();
        Assert.NotNull(pair);
        const string message = "round-trip-payload-{\"token\":\"abc123\"}";
        var blob = CryptoUtils.EncryptWithPublicKeyEC(message, pair!.PublicKey);
        Assert.NotNull(blob);
        Assert.Equal(message, CryptoUtils.DecryptWithPrivateKeyEC(blob!, pair.PrivateKey));
    }

    [Fact]
    public void GenerateServerKeyPairEC_ProducesUsableKeys()
    {
        var pair = CryptoUtils.GenerateServerKeyPairEC();
        Assert.NotNull(pair);

        const string message = "generated-keypair-check";
        var blob = CryptoUtils.EncryptWithPublicKeyEC(message, pair!.PublicKey);
        Assert.NotNull(blob);
        Assert.Equal(message, CryptoUtils.DecryptWithPrivateKeyEC(blob!, pair.PrivateKey));
    }

    // Reverse direction: a C#-produced Tink blob must decrypt with the npm
    // library's exact primitives (Node). Skips when Node isn't on PATH so CI
    // without Node stays green; the forward + round-trip tests still run.
    [Fact]
    public void Ecies_CSharpEncrypt_NodeCanDecrypt()
    {
        if (!NodeAvailable())
        {
            return;
        }

        var pair = CryptoUtils.GenerateServerKeyPairEC();
        Assert.NotNull(pair);
        const string message = "reverse-interop-payload-abc123";
        var blob = CryptoUtils.EncryptWithPublicKeyEC(message, pair!.PublicKey);
        Assert.NotNull(blob);

        var blobFile = Path.GetTempFileName();
        var privFile = Path.GetTempFileName();
        try
        {
            File.WriteAllText(blobFile, blob!);
            File.WriteAllText(privFile, pair.PrivateKey);
            var output = RunNode($"\"{GenScriptPath()}\" decrypt \"{blobFile}\" \"{privFile}\"");
            Assert.Equal(message, output);
        }
        finally
        {
            File.Delete(blobFile);
            File.Delete(privFile);
        }
    }

    // Generates a fresh cross-language vector via Node; null when Node is unavailable.
    private static NodeVector? GenerateNodeVector()
    {
        if (!NodeAvailable())
        {
            return null;
        }
        var json = RunNode($"\"{GenScriptPath()}\" gen");
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        return new NodeVector(
            root.GetProperty("authPublicKey").GetString()!,
            root.GetProperty("signedData").GetString()!,
            root.GetProperty("signature").GetString()!,
            root.GetProperty("recipientPublicKey").GetString()!,
            root.GetProperty("recipientPrivateKey").GetString()!,
            root.GetProperty("plaintext").GetString()!,
            root.GetProperty("tinkCiphertext").GetString()!);
    }

    private static string GenScriptPath([CallerFilePath] string thisFile = "")
        => Path.Combine(Path.GetDirectoryName(thisFile)!, "Interop", "gen.js");

    private static bool NodeAvailable()
    {
        try
        {
            return RunNode("--version").StartsWith('v');
        }
        catch
        {
            return false;
        }
    }

    private static string RunNode(string arguments)
    {
        var psi = new ProcessStartInfo("node", arguments)
        {
            RedirectStandardOutput = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
        };
        using var process = Process.Start(psi)!;
        var stdout = process.StandardOutput.ReadToEnd();
        process.WaitForExit();
        return stdout.Trim();
    }
}
