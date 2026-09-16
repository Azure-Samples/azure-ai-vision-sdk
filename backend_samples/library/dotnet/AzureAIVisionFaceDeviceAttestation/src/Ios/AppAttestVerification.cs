using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

using static Azure.AI.Vision.Face.DeviceAttestation.Ios.AppAttestConstants;

namespace Azure.AI.Vision.Face.DeviceAttestation.Ios;

/// <summary>
/// Apple App Attest verification: the registration-time attestation chain (nine
/// phases) and the per-call ongoing assertion. Faithful port of the npm
/// library's ios/ modules.
/// </summary>
internal static class AppAttestVerification
{
    /// <summary>A phase failure carrying a stable reason code and message.</summary>
    private sealed class PhaseFail : Exception
    {
        public string Reason { get; }
        public PhaseFail(string reason, string message) : base(message) => Reason = reason;
    }

    private sealed record AssertionResult(
        byte[] AssertionAuthData,
        byte[] AssertionRpIdHash,
        int AssertionFlags,
        long AssertionSignCount,
        string SignatureEncoding,
        string AuthCertThumbprintHex);

    public static AuthVerificationResult Verify(
        AttestationConfig config,
        IAttestationLogger logger,
        AttestationMessageData messageData,
        string attestJson)
    {
        logger.TrackEvent("IosAuth.VerifyStart", new Dictionary<string, object?>
        {
            ["platform"] = "ios",
            ["clientId"] = messageData.ClientId,
            ["attestJsonLength"] = attestJson.Length,
        });

        try
        {
            bool debugMode = config.DebugMode;

            // 1. parse JSON + CBOR envelopes
            var (token, assertion) = ParseEnvelope(attestJson);

            using var credCert = X509CertificateLoader.LoadCertificate(token.CredCertDer);
            using var intCert = X509CertificateLoader.LoadCertificate(token.IntermediateDer);

            ValidateChainValidityDates(credCert, intCert);
            VerifyX5cChain(token.CredCertDer, token.IntermediateDer);

            var warnings = new List<string>();

            // 4. authData layout
            var ad = ParseAttestAuthData(token.AuthData);

            // 5. rpIdHash == SHA-256(IOS_APP_ID)
            var expectedAppId = VerifyAppIdRpIdHash(ad.RpIdHash, config);

            // 6. aaguid policy (prod/dev gating)
            VerifyAaguidPolicy(ad.Aaguid, debugMode, warnings);

            // 7. credentialId == SHA-256(uncompressed EC point)
            using var credPubKey = credCert.GetECDsaPublicKey()
                ?? throw new PhaseFail("CRED_PUBKEY_EXTRACT", "Failed to extract credCert public key");
            VerifyCredentialIdBindsPubKey(credPubKey, ad.CredentialId);

            // 8. attestation nonce binding to the session challenge
            var (certNonce, challengeBytes) = VerifyAttestationNonceBinding(token.CredCertDer, token.AuthData, messageData);

            // 9. assertion proves the auth cert belongs to the attested key
            var asr = VerifyAssertionAgainstAuthCert(assertion, credPubKey, ad.RpIdHash, ad.SignCount, messageData.PublicCert!);

            var aaguidUtf8 = Encoding.ASCII.GetString(ad.Aaguid).TrimEnd('\0');

            var verdict = new AppAttestVerdict
            {
                Fmt = token.Fmt,
                RpIdHash = Hex(ad.RpIdHash),
                AppId = expectedAppId,
                Aaguid = aaguidUtf8,
                Flags = ad.Flags,
                SignCount = ad.SignCount,
                CredentialId = Hex(ad.CredentialId),
                CredentialIdMatchesPubKey = true,
                CredCert = CertInfo(credCert, Hex(SHA256.HashData(token.CredCertDer))),
                CredCertPem = credCert.ExportCertificatePem(),
                IntermediateCert = CertInfo(intCert, null),
                ReceiptLength = token.ReceiptLength,
                Receipt = token.Receipt is not null ? Convert.ToBase64String(token.Receipt) : null,
                AuthDataLength = token.AuthData.Length,
                NonceExtension = Hex(certNonce),
                ExpectedClientDataHash = Hex(challengeBytes),
                AuthCertThumbprint = asr.AuthCertThumbprintHex,
                NonceVerified = true,
                Assertion = new AppAttestAssertionInfo
                {
                    AuthenticatorDataLength = asr.AssertionAuthData.Length,
                    RpIdHash = Hex(asr.AssertionRpIdHash),
                    Flags = asr.AssertionFlags,
                    SignCount = asr.AssertionSignCount,
                    SignatureLength = assertion.Signature.Length,
                    SignatureEncoding = asr.SignatureEncoding,
                    ExpectedClientDataHash = asr.AuthCertThumbprintHex,
                    SignatureVerified = true,
                },
                ChallengeBinding =
                    "attest: sha256(authData || challengeHashBytes);  assert: ECDSA-SHA256(credCertPubKey, nonce = sha256(authenticatorData || sha256(authCertDER)))",
            };

            logger.TrackEvent("IosAuth.VerifySuccess", new Dictionary<string, object?>
            {
                ["clientId"] = messageData.ClientId,
                ["aaguid"] = aaguidUtf8,
                ["attestSignCount"] = ad.SignCount,
                ["assertSignCount"] = asr.AssertionSignCount,
                ["warningCount"] = warnings.Count,
            });

            return new AuthVerificationResult
            {
                Verified = true,
                Platform = "ios",
                Message = "iOS App Attest verified successfully",
                Timestamp = IsoTime.Now(),
                ChainLength = 2,
                RootCA = "Apple App Attestation Root CA",
                AppAttestVerdict = verdict,
                Warnings = warnings.Count > 0 ? warnings : null,
            };
        }
        catch (PhaseFail f)
        {
            logger.TrackEvent("IosAuth.VerifyFail", new Dictionary<string, object?>
            {
                ["clientId"] = messageData.ClientId,
                ["reason"] = f.Reason,
                ["message"] = f.Message,
            });
            return new AuthVerificationResult { Verified = false, Platform = "ios", Message = f.Message, Timestamp = IsoTime.Now() };
        }
        catch (Exception e)
        {
            logger.TrackException(e, new Dictionary<string, object?> { ["source"] = "verifyiOSAuth.unexpected", ["clientId"] = messageData.ClientId });
            return new AuthVerificationResult
            {
                Verified = false,
                Platform = "ios",
                Message = $"iOS attestation verification error: {e.Message}",
                Timestamp = IsoTime.Now(),
            };
        }
    }

    public static OngoingAssertionResult VerifyOngoingAssertion(
        string credCertPem,
        byte[] blob,
        string assertionB64,
        string expectedRpIdHashHex,
        long lastSignCount)
    {
        X509Certificate2? credCert = null;
        ECDsa? pub = null;
        try
        {
            try
            {
                credCert = X509Certificate2.CreateFromPem(credCertPem);
                pub = credCert.GetECDsaPublicKey();
            }
            catch (Exception e)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "CRED_CERT_PARSE_FAIL", Message = $"Failed to parse persisted credCert PEM: {e.Message}" };
            }
            if (pub is null)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "CRED_CERT_PARSE_FAIL", Message = "Failed to parse persisted credCert PEM: not an EC key" };
            }

            AppAttestAssertionObject parsed;
            try
            {
                parsed = AppAttestParsers.ParseAppAttestAssertion(assertionB64);
            }
            catch (Exception e)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "ASSERTION_DECODE_ERROR", Message = $"Failed to decode App Attest assertion: {e.Message}" };
            }

            var authData = parsed.AuthenticatorData;
            var (rpIdHash, _, signCount) = AppAttestParsers.ParseAssertionAuthData(authData);

            if (Hex(rpIdHash) != expectedRpIdHashHex)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "ASSERTION_RPID_MISMATCH", Message = "Assertion rpIdHash does not match SHA-256(IOS_APP_ID)", SignCount = signCount };
            }
            if (signCount <= lastSignCount)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "ASSERTION_SIGNCOUNT_NOT_INCREMENTED", Message = $"Assertion signCount ({signCount}) is not greater than lastSignCount ({lastSignCount})", SignCount = signCount };
            }

            var clientDataHash = SHA256.HashData(blob);
            byte[] nonce;
            using (var ih = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
            {
                ih.AppendData(authData);
                ih.AppendData(clientDataHash);
                nonce = ih.GetHashAndReset();
            }

            bool ok;
            try
            {
                ok = pub.VerifyData(nonce, parsed.Signature, HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence)
                     || pub.VerifyData(nonce, parsed.Signature, HashAlgorithmName.SHA256, DSASignatureFormat.IeeeP1363FixedFieldConcatenation);
            }
            catch (Exception e)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "ASSERTION_VERIFY_EXCEPTION", Message = $"Assertion ECDSA verify threw: {e.Message}", SignCount = signCount };
            }
            if (!ok)
            {
                return new OngoingAssertionResult { Ok = false, Reason = "ASSERTION_SIGNATURE_INVALID", Message = "Assertion signature does not verify against credCert public key", SignCount = signCount };
            }

            return new OngoingAssertionResult { Ok = true, SignCount = signCount };
        }
        finally
        {
            pub?.Dispose();
            credCert?.Dispose();
        }
    }

    // --- phases ---

    private static (AppAttestObject Token, AppAttestAssertionObject Assertion) ParseEnvelope(string attestJson)
    {
        JsonObject? parsed;
        try
        {
            parsed = JsonNode.Parse(attestJson) as JsonObject;
        }
        catch (JsonException)
        {
            throw new PhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson");
        }

        var attestation = parsed is null ? null : JsonHelpers.GetString(parsed, "attestation");
        var assertion = parsed is null ? null : JsonHelpers.GetString(parsed, "assertion");
        if (string.IsNullOrEmpty(attestation))
        {
            throw new PhaseFail("MISSING_ATTESTATION", "Invalid attestation: missing attestation field");
        }
        if (string.IsNullOrEmpty(assertion))
        {
            throw new PhaseFail("MISSING_ASSERTION", "Invalid attestation: missing assertion (auth-cert binding proof)");
        }

        AppAttestObject token;
        try
        {
            token = AppAttestParsers.ParseAppAttestToken(attestation!);
        }
        catch (Exception e)
        {
            throw new PhaseFail("ATTESTATION_DECODE_ERROR", $"Invalid attestation: {e.Message}");
        }
        if (token.Fmt != "apple-appattest")
        {
            throw new PhaseFail("UNEXPECTED_FMT", $"Unexpected fmt: {token.Fmt}");
        }

        AppAttestAssertionObject parsedAssertion;
        try
        {
            parsedAssertion = AppAttestParsers.ParseAppAttestAssertion(assertion!);
        }
        catch (Exception e)
        {
            throw new PhaseFail("ASSERTION_DECODE_ERROR", $"Invalid assertion: {e.Message}");
        }
        return (token, parsedAssertion);
    }

    private static void VerifyX5cChain(byte[] credCertDer, byte[] intermediateDer)
    {
        var chain = new List<byte[]> { credCertDer, intermediateDer };
        if (!CertUtils.MatchesPinnedCA(intermediateDer, AppAttestConstants.AppleAppAttestRootCAs))
            chain.Add(CertUtils.PemToDer(AppAttestConstants.AppleAppAttestRootCaPem));
        if (!CertUtils.ValidateCertificatePath(chain, AppAttestConstants.AppleAppAttestRootCAs))
            throw new PhaseFail("CHAIN_PATH_INVALID", "Certificate path validation failed against the Apple App Attestation Root CA");
    }

    private static void ValidateChainValidityDates(X509Certificate2 credCert, X509Certificate2 intCert)
    {
        var now = DateTimeOffset.UtcNow;
        foreach (var (label, c) in new[] { ("credCert", credCert), ("intermediate", intCert) })
        {
            var vf = new DateTimeOffset(c.NotBefore.ToUniversalTime());
            var vt = new DateTimeOffset(c.NotAfter.ToUniversalTime());
            if (now < vf || now > vt)
            {
                throw new PhaseFail("CHAIN_CERT_NOT_VALID", $"{label} is not valid (valid from {vf:o} to {vt:o})");
            }
        }
    }

    private static (byte[] RpIdHash, int Flags, long SignCount, byte[] Aaguid, int CredIdLen, byte[] CredentialId) ParseAttestAuthData(byte[] authData)
    {
        if (authData.Length < AuthDataHeaderBytes)
        {
            throw new PhaseFail("AUTHDATA_TOO_SHORT", $"authData is {authData.Length} bytes, < {AuthDataHeaderBytes}");
        }
        var (rpIdHash, flags, signCount) = AppAttestParsers.ParseAssertionAuthData(authData);
        if (authData.Length < CredentialIdOffset)
        {
            throw new PhaseFail("AUTHDATA_MISSING_ATTESTED", "authData missing attested credential data");
        }
        var aaguid = authData[AaguidOffset..CredentialIdLengthOffset];
        int credIdLen = BinaryPrimitives.ReadUInt16BigEndian(authData.AsSpan(CredentialIdLengthOffset));
        if (authData.Length < CredentialIdOffset + credIdLen)
        {
            throw new PhaseFail("AUTHDATA_TRUNCATED", "authData truncated within credentialId");
        }
        var credentialId = authData[CredentialIdOffset..(CredentialIdOffset + credIdLen)];
        return (rpIdHash, flags, signCount, aaguid, credIdLen, credentialId);
    }

    private static string VerifyAppIdRpIdHash(byte[] rpIdHash, AttestationConfig config)
    {
        var expectedAppId = config.IosAppId;
        if (string.IsNullOrEmpty(expectedAppId))
        {
            throw new PhaseFail("MISSING_APP_ID_ENV", "IOS_APP_ID environment variable not set (expected \"<TeamID>.<BundleID>\")");
        }
        var expectedRpIdHash = SHA256.HashData(Encoding.UTF8.GetBytes(expectedAppId));
        if (!expectedRpIdHash.AsSpan().SequenceEqual(rpIdHash))
        {
            throw new PhaseFail("APP_ID_MISMATCH", "authData.rpIdHash does not match SHA-256(IOS_APP_ID)");
        }
        return expectedAppId;
    }

    private static void VerifyAaguidPolicy(byte[] aaguid, bool debugMode, List<string> warnings)
    {
        bool isProd = aaguid.AsSpan().SequenceEqual(AppAttestConstants.AaguidProd);
        bool isDev = aaguid.AsSpan().SequenceEqual(AppAttestConstants.AaguidDev);
        if (!isProd && !isDev)
        {
            throw new PhaseFail("UNKNOWN_AAGUID", $"Unknown aaguid in authData: {Hex(aaguid)}");
        }
        if (isDev && !debugMode)
        {
            throw new PhaseFail("DEV_AAGUID_NOT_ALLOWED", "authData.aaguid is \"appattestdevelop\" but DEBUG_MODE is not enabled");
        }
        if (isDev)
        {
            warnings.Add("aaguid is \"appattestdevelop\" (debug mode)");
        }
    }

    private static void VerifyCredentialIdBindsPubKey(ECDsa credPubKey, byte[] credentialId)
    {
        var point = UncompressedPoint(credPubKey);
        if (point.Length != 65 || point[0] != 0x04)
        {
            throw new PhaseFail("CRED_PUBKEY_FORMAT", "credCert public key is not uncompressed P-256");
        }
        var expected = SHA256.HashData(point);
        if (!expected.AsSpan().SequenceEqual(credentialId))
        {
            throw new PhaseFail("CREDENTIAL_ID_MISMATCH", "credentialId in authData does not match SHA-256 of credCert public key");
        }
    }

    private static (byte[] CertNonce, byte[] ChallengeBytes) VerifyAttestationNonceBinding(byte[] credCertDer, byte[] authData, AttestationMessageData messageData)
    {
        if (string.IsNullOrEmpty(messageData.PublicCert))
        {
            throw new PhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data");
        }
        if (string.IsNullOrEmpty(messageData.ChallengeHash))
        {
            throw new PhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data");
        }
        if (!IsHex64(messageData.ChallengeHash!))
        {
            throw new PhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a 64-char hex string");
        }
        var challengeBytes = Convert.FromHexString(messageData.ChallengeHash!);
        byte[] expectedNonce;
        using (var ih = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
        {
            ih.AppendData(authData);
            ih.AppendData(challengeBytes);
            expectedNonce = ih.GetHashAndReset();
        }

        var certNonce = AppAttestParsers.ExtractNonceFromCredCert(credCertDer);
        if (certNonce is null)
        {
            throw new PhaseFail("CRED_NONCE_EXT_MISSING", "credCert is missing the App Attest nonce extension (OID 1.2.840.113635.100.8.2)");
        }
        if (!expectedNonce.AsSpan().SequenceEqual(certNonce))
        {
            throw new PhaseFail("CHALLENGE_NONCE_MISMATCH", "App Attest challenge binding failed: cert nonce does not match SHA-256(authData || challengeHash bytes)");
        }
        return (certNonce, challengeBytes);
    }

    private static AssertionResult VerifyAssertionAgainstAuthCert(
        AppAttestAssertionObject assertion,
        ECDsa credPubKey,
        byte[] attestRpIdHash,
        long attestSignCount,
        string authCertPem)
    {
        var authCertDer = CertUtils.PemToDer(authCertPem);
        var authCertThumbprint = SHA256.HashData(authCertDer);
        var authCertThumbprintHex = Hex(authCertThumbprint);

        var assertionAuthData = assertion.AuthenticatorData;
        var (assertionRpIdHash, assertionFlags, assertionSignCount) = AppAttestParsers.ParseAssertionAuthData(assertionAuthData);

        if (!assertionRpIdHash.AsSpan().SequenceEqual(attestRpIdHash))
        {
            throw new PhaseFail("ASSERTION_RPID_MISMATCH", "Assertion rpIdHash does not match attestation rpIdHash");
        }
        if (assertionSignCount <= attestSignCount)
        {
            throw new PhaseFail("ASSERTION_SIGNCOUNT_NOT_INCREMENTED",
                $"Assertion signCount ({assertionSignCount}) is not greater than attestation signCount ({attestSignCount})");
        }

        byte[] assertionNonce;
        using (var ih = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
        {
            ih.AppendData(assertionAuthData);
            ih.AppendData(authCertThumbprint);
            assertionNonce = ih.GetHashAndReset();
        }

        bool ok = false;
        string encoding = "der";
        try
        {
            ok = credPubKey.VerifyData(assertionNonce, assertion.Signature, HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);
            if (!ok)
            {
                encoding = "ieee-p1363";
                ok = credPubKey.VerifyData(assertionNonce, assertion.Signature, HashAlgorithmName.SHA256, DSASignatureFormat.IeeeP1363FixedFieldConcatenation);
            }
        }
        catch
        {
            // fall through to the failure below
        }
        if (!ok)
        {
            throw new PhaseFail("ASSERTION_SIGNATURE_INVALID",
                "Assertion signature does not verify against credCert public key for nonce = SHA-256(authenticatorData || sha256(authCertDER))");
        }

        return new AssertionResult(assertionAuthData, assertionRpIdHash, assertionFlags, assertionSignCount, encoding, authCertThumbprintHex);
    }

    // --- helpers ---

    private static string Hex(byte[] bytes) => Convert.ToHexString(bytes).ToLowerInvariant();

    private static bool IsHex64(string s) => s.Length == 64 && s.All(Uri.IsHexDigit);

    private static byte[] UncompressedPoint(ECDsa key)
    {
        var p = key.ExportParameters(false);
        var x = LeftPad(p.Q.X!, 32);
        var y = LeftPad(p.Q.Y!, 32);
        var point = new byte[65];
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
        var result = new byte[length];
        Buffer.BlockCopy(value, 0, result, length - value.Length, value.Length);
        return result;
    }

    private static AppAttestCertInfo CertInfo(X509Certificate2 c, string? thumbprint) => new()
    {
        Subject = c.Subject,
        Issuer = c.Issuer,
        SerialNumber = c.SerialNumber,
        ValidFrom = c.NotBefore.ToUniversalTime().ToString("o"),
        ValidTo = c.NotAfter.ToUniversalTime().ToString("o"),
        Thumbprint = thumbprint,
    };
}
