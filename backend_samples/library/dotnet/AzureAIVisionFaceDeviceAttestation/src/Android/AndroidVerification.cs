using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>Outer JSON the Android client posts.</summary>
internal sealed class AndroidAttestationJson
{
    public string? Token { get; init; }
    public List<string>? CertificateChain { get; init; }
}

/// <summary>
/// Android Key Attestation chain verification + Play Integrity verdict checks.
/// Faithful port of the npm library's android/ modules.
/// </summary>
internal static class AndroidVerification
{
    private sealed class PhaseFail : Exception
    {
        public string Reason { get; }
        public PhaseFail(string reason, string message) : base(message) => Reason = reason;
    }

    public static async Task<AuthVerificationResult> VerifyAsync(
        AttestationConfig config,
        IAttestationLogger logger,
        AttestationMessageData messageData,
        string attestJson)
    {
        logger.TrackEvent("AndroidAuth.VerifyStart", new Dictionary<string, object?>
        {
            ["platform"] = "android",
            ["clientId"] = messageData.ClientId,
            ["attestJsonLength"] = attestJson.Length,
        });

        try
        {
            bool debugMode = config.DebugMode;

            // 1. parse + structural validation
            var attestData = ParseAttestJson(attestJson);

            // 2. build DER buffers from base64 chain + PEM leaf
            var (chain, leaf) = BuildChainDerBuffers(attestData, messageData.PublicCert);

            // 3. leaf <-> chain[0] binding
            VerifyLeafBoundToChain0(leaf, chain);

            // 4. validity dates (leaf warning, chain hard-fail)
            var leafCertValidityWarning = ValidateChainValidityDates(leaf, chain, logger);

            // 5. chain signatures + pinned root CA
            var rootCASubject = VerifyChainSignaturesAndRoot(chain);

            // 6. attestation must be issued by TEE or StrongBox
            VerifyHardwareAttestationSecurityLevel(chain[0]);

            // 7. revocation list lookup (fails closed)
            await VerifyChainRevocationsAsync(leaf, chain, logger);

            // 8. keymaster extension binds the session challenge
            var chainChallengeHex = VerifyChainBindsSessionChallenge(chain[0], messageData.ChallengeHash);

            // 9. Play Integrity verdict. Required, unless the API was unavailable /
            //    quota-exceeded and the operator opted into
            //    AllowAndroidAttestationWhenGoogleUnavailable — in which case the
            //    hardware Key Attestation above (incl. the session-bound keymaster
            //    challenge in step 7) stands on its own and the verdict is skipped.
            if (string.IsNullOrEmpty(attestData.Token))
            {
                throw new PhaseFail("MISSING_INTEGRITY_TOKEN", "Play Integrity token is required but not provided");
            }
            var integrityResult = await PlayIntegrityApi.DecryptAndVerifyIntegrityVerdictAsync(config, logger, attestData.Token!);

            var warnings = new List<string>();
            PlayIntegrityVerdict? integrityVerdict = null;

            if (integrityResult.Ok)
            {
                integrityVerdict = integrityResult.Verdict;
            }
            else if (integrityResult.Tolerable && config.AllowAndroidAttestationWhenGoogleUnavailable)
            {
                var warning = $"Play Integrity verdict unavailable ({integrityResult.Reason}); accepted on Key Attestation alone via AllowAndroidAttestationWhenGoogleUnavailable";
                warnings.Add(warning);
                logger.TrackEvent("AndroidAuth.IntegrityUnavailableAccepted", new Dictionary<string, object?>
                {
                    ["clientId"] = messageData.ClientId,
                    ["reason"] = integrityResult.Reason,
                });
            }
            else
            {
                throw new PhaseFail("INTEGRITY_DECRYPT_FAIL", "Play Integrity verification failed: could not decrypt or verify integrity token");
            }

            if (integrityVerdict is not null)
            {
                integrityVerdict.AttestationChallenge = chainChallengeHex;

                // 10. requestHash binds the integrity token to the auth key
                ThrowIfFailed(IntegrityChecks.VerifyRequestHash(integrityVerdict, leaf));

                // 11. timestamp freshness
                ThrowIfFailed(IntegrityChecks.VerifyTimestamp(integrityVerdict));

                // 12. appIntegrity + deviceIntegrity + environment warnings
                ThrowIfFailed(IntegrityChecks.Evaluate(integrityVerdict, config, debugMode, warnings));
            }

            logger.TrackEvent("AndroidAuth.VerifySuccess", new Dictionary<string, object?>
            {
                ["clientId"] = messageData.ClientId,
                ["chainLength"] = chain.Count + 1,
                ["rootCA"] = rootCASubject,
                ["integritySkipped"] = integrityVerdict is null,
                ["warningCount"] = warnings.Count,
            });

            return new AuthVerificationResult
            {
                Verified = true,
                Platform = "android",
                Message = integrityVerdict is not null
                    ? "Android Key Attestation and Play Integrity verified successfully"
                    : "Android Key Attestation verified successfully (Play Integrity unavailable, accepted by policy)",
                Timestamp = IsoTime.Now(),
                ChainLength = chain.Count + 1,
                RootCA = rootCASubject,
                IntegrityVerdict = integrityVerdict,
                LeafCertValidityWarning = leafCertValidityWarning,
                Warnings = warnings.Count > 0 ? warnings : null,
            };
        }
        catch (PhaseFail f)
        {
            logger.TrackEvent("AndroidAuth.VerifyFail", new Dictionary<string, object?>
            {
                ["clientId"] = messageData.ClientId,
                ["reason"] = f.Reason,
                ["message"] = f.Message,
            });
            return new AuthVerificationResult { Verified = false, Platform = "android", Message = f.Message, Timestamp = IsoTime.Now() };
        }
        catch (Exception e)
        {
            logger.TrackException(e, new Dictionary<string, object?> { ["source"] = "verifyAndroidAuth.unexpected", ["clientId"] = messageData.ClientId });
            return new AuthVerificationResult
            {
                Verified = false,
                Platform = "android",
                Message = $"Android attestation verification error: {e.Message}",
                Timestamp = IsoTime.Now(),
            };
        }
    }

    // --- chain phases ---

    private static AndroidAttestationJson ParseAttestJson(string attestJson)
    {
        AndroidAttestationJson? attestData;
        try
        {
            attestData = JsonSerializer.Deserialize<AndroidAttestationJson>(attestJson, LibraryJson.Metadata);
        }
        catch (JsonException)
        {
            throw new PhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson");
        }
        if (attestData?.CertificateChain is null)
        {
            throw new PhaseFail("INVALID_CHAIN_FORMAT", "Invalid attestation: missing or invalid certificateChain");
        }
        if (attestData.CertificateChain.Count == 0)
        {
            throw new PhaseFail("EMPTY_CHAIN", "Invalid attestation: empty certificateChain");
        }
        return attestData;
    }

    private static (List<byte[]> Chain, byte[] Leaf) BuildChainDerBuffers(AndroidAttestationJson attestData, string? publicCertPem)
    {
        if (string.IsNullOrEmpty(publicCertPem))
        {
            throw new PhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data");
        }
        var chain = attestData.CertificateChain!.Select(Base64Utils.Decode).ToList();
        var leaf = CertUtils.PemToDer(publicCertPem);
        return (chain, leaf);
    }

    private static void VerifyLeafBoundToChain0(byte[] leaf, List<byte[]> chain)
    {
        bool leafMatchesChain0 = leaf.AsSpan().SequenceEqual(chain[0]);
        if (leafMatchesChain0)
        {
            return;
        }
        throw new PhaseFail("LEAF_CHAIN_MISMATCH",
            "Certificate chain verification failed: publicCert must match certificateChain[0]");
    }

    private static string? ValidateChainValidityDates(byte[] leaf, List<byte[]> chain, IAttestationLogger logger)
    {
        var now = DateTimeOffset.UtcNow;
        string? leafCertValidityWarning = null;

        using (var leafCert = X509CertificateLoader.LoadCertificate(leaf))
        {
            var vf = new DateTimeOffset(leafCert.NotBefore.ToUniversalTime());
            var vt = new DateTimeOffset(leafCert.NotAfter.ToUniversalTime());
            if (now < vf || now > vt)
            {
                leafCertValidityWarning = $"Leaf certificate validity warning: valid from {vf:o} to {vt:o}";
                logger.TrackEvent("AndroidAuth.LeafCertValidityWarning", new Dictionary<string, object?> { ["validFrom"] = vf.ToString("o"), ["validTo"] = vt.ToString("o") });
            }
        }

        for (int i = 0; i < chain.Count; i++)
        {
            using var cert = X509CertificateLoader.LoadCertificate(chain[i]);
            var vf = new DateTimeOffset(cert.NotBefore.ToUniversalTime());
            var vt = new DateTimeOffset(cert.NotAfter.ToUniversalTime());
            if (now < vf || now > vt)
            {
                throw new PhaseFail("CHAIN_CERT_NOT_VALID", $"Certificate at index {i} is not valid (valid from {vf:o} to {vt:o})");
            }
        }
        return leafCertValidityWarning;
    }

    private static string VerifyChainSignaturesAndRoot(List<byte[]> chain)
    {
        var root = chain[^1];
        if (!CertUtils.MatchesPinnedCA(root, GoogleRoots.GoogleHardwareAttestationRootCAs))
        {
            throw new PhaseFail("ROOT_CA_MISMATCH", "Certificate chain verification failed: root CA does not match any pinned CA");
        }
        if (!CertUtils.ValidateCertificatePath(chain, GoogleRoots.GoogleHardwareAttestationRootCAs))
        {
            throw new PhaseFail("CHAIN_PATH_INVALID", "Certificate path validation failed");
        }

        try
        {
            using var rootCert = X509CertificateLoader.LoadCertificate(root);
            return rootCert.Subject;
        }
        catch
        {
            return "unknown";
        }
    }

    private static void VerifyHardwareAttestationSecurityLevel(byte[] chain0Der)
    {
        var description = KeymasterExt.ParseKeyDescription(chain0Der);
        if (description is null)
        {
            throw new PhaseFail("KEYMASTER_EXT_MISSING",
                "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert");
        }
        if (!KeymasterExt.IsHardwareAttestationSecurityLevel(description))
        {
            throw new PhaseFail("ATTESTATION_SECURITY_LEVEL_INVALID",
                "Android Key Attestation verification failed: attestationSecurityLevel must be TrustedEnvironment or StrongBox");
        }
    }

    private static async Task VerifyChainRevocationsAsync(byte[] leaf, List<byte[]> chain, IAttestationLogger logger)
    {
        var statusList = await Revocation.FetchRevocationStatusListAsync(logger);

        var leafRevocation = Revocation.CheckCertificateRevocation(leaf, statusList);
        if (leafRevocation.IsRevoked)
        {
            throw new PhaseFail("LEAF_REVOKED", $"Leaf certificate has been {leafRevocation.Status}: {leafRevocation.Reason}");
        }

        for (int i = 0; i < chain.Count; i++)
        {
            var revocation = Revocation.CheckCertificateRevocation(chain[i], statusList);
            if (revocation.IsRevoked)
            {
                throw new PhaseFail("CHAIN_CERT_REVOKED", $"Certificate at index {i} has been {revocation.Status}: {revocation.Reason}");
            }
        }
    }

    private static string VerifyChainBindsSessionChallenge(byte[] chain0Der, string? sessionChallengeHash)
    {
        if (string.IsNullOrEmpty(sessionChallengeHash))
        {
            throw new PhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data");
        }
        if (sessionChallengeHash.Length % 2 != 0 || !sessionChallengeHash.All(Uri.IsHexDigit))
        {
            throw new PhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a hex string of even length");
        }
        var sessionChallengeBytes = Convert.FromHexString(sessionChallengeHash);
        var chainChallenge = KeymasterExt.ExtractAttestationChallengeFromCert(chain0Der);
        if (chainChallenge is null)
        {
            throw new PhaseFail("KEYMASTER_EXT_MISSING",
                "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert");
        }
        if (!chainChallenge.AsSpan().SequenceEqual(sessionChallengeBytes))
        {
            throw new PhaseFail("CHAIN_CHALLENGE_MISMATCH", "Android Key Attestation chain challenge does not match session challengeHash");
        }
        return Convert.ToHexString(chainChallenge).ToLowerInvariant();
    }

    private static void ThrowIfFailed(IntegrityCheckResult result)
    {
        if (!result.Ok)
        {
            throw new PhaseFail(result.Reason!, result.Message!);
        }
    }
}
