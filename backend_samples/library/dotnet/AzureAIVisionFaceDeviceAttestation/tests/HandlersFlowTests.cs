using System.Text.Json;
using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Services;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;
using Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

/// <summary>
/// End-to-end handler flow using Android (which does not run the iOS assertion
/// path). The register step's platform attestation is covered in P5; this
/// pre-registers the cert directly and drives challenge -> verify -> token ->
/// digest with real client-side signing + ECIES.
/// </summary>
public class HandlersFlowTests
{
    private const string ClientId = "com.example.app";

    private static AttestationService NewService(InMemoryClusterStore store)
        => AttestationService.Create(new AttestationConfig(), store);

    [Fact]
    public async Task Cas_RejectsStaleVersionsAndDuplicateCreation()
    {
        var store = new InMemoryClusterStore();
        var record = new SessionRecord { Token = "token", Data = new JsonObject() };
        Assert.True(await store.SetSessionAsync("sid", record));
        Assert.False(await store.SetSessionAsync("sid", record));
        var snapshot = (await store.GetSessionAsync("sid"))!;
        snapshot.Value.Data["changed"] = true;
        Assert.Null((await store.GetSessionAsync("sid"))!.Value.Data["changed"]);
        Assert.Equal(UpdateResult.Applied, await store.UpdateSessionAsync("sid", snapshot.Version, snapshot.Value));
        Assert.Equal(UpdateResult.Conflict, await store.UpdateSessionAsync("sid", snapshot.Version, record));
        Assert.Equal(UpdateResult.MissingOrExpired, await store.UpdateSessionAsync("missing", snapshot.Version, record));
    }

    [Fact]
    public async Task ConcurrentSignedTokenRequests_OneWinner_StorageFailureReleasesNothing()
    {
        var store = new InMemoryClusterStore();
        var service = NewService(store);
        var client = new TestAttestationClient();
        var serverKeys = CryptoUtils.GenerateServerKeyPairEC()!;
        var sid = Guid.NewGuid().ToString();
        var data = new JsonObject
        {
            ["serverKeyGenerated"] = true, ["certRegistered"] = true,
            ["system"] = "android", ["clientId"] = ClientId, ["challengeHash"] = "challenge",
            ["clientAuthPublicKey"] = CertUtils.ExtractPublicKeyFromCert(client.AuthCertPem),
            ["clientEncryptionPublicKey"] = CertUtils.ExtractPublicKeyFromCert(client.EncCertPem),
            ["serverEncryptionPrivateKey"] = serverKeys.PrivateKey,
        };
        await store.SetSessionAsync(sid, new SessionRecord { Token = "secret-token", Data = data });
        var encrypted = CryptoUtils.EncryptWithPublicKeyEC(JsonSerializer.Serialize(new
            { challengeHash = "challenge", clientId = ClientId, system = "android" }), serverKeys.PublicKey)!;
        var request = new SessionTokenRequest { SessionId = sid, Body = new SessionTokenBody
            { EncryptedData = encrypted, Signature = client.Sign(encrypted) } };
        var arrived = 0;
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        store.AfterSessionRead = () =>
        {
            if (Interlocked.Increment(ref arrived) == 2) gate.SetResult();
            return gate.Task;
        };
        var results = await Task.WhenAll(service.SessionTokenAsync(request), service.SessionTokenAsync(request));
        Assert.Equal(new[] { 200, 409 }, results.Select(result => result.Status).Order().ToArray());
        var winner = Assert.IsType<SessionTokenSuccess>(results.Single(result => result.Ok).Body);
        Assert.Contains("secret-token", CryptoUtils.DecryptWithPrivateKeyEC(winner.EncryptedData, client.EncPrivateKeyPem));
        store.AfterSessionRead = null;
        request = new SessionTokenRequest { SessionId = Guid.NewGuid().ToString(), Body = request.Body };
        await store.SetSessionAsync(request.SessionId, new SessionRecord { Token = "secret-token", Data = data });
        store.FailWrites = true;
        var failure = await service.SessionTokenAsync(request);
        Assert.Equal(503, failure.Status);
        Assert.IsNotType<SessionTokenSuccess>(failure.Body);
        Assert.Null((await store.GetSessionAsync(request.SessionId))!.Value.Data["authCompleted"]);
    }

    [Theory]
    [InlineData("12345678-1234-1234-1234-123456789abc")]
    [InlineData("12345678-1234-1234-1234-123456789ABC")]
    [InlineData("00000000-0000-0000-0000-000000000000")]
    [InlineData("ffffffff-ffff-ffff-ffff-ffffffffffff")]
    public async Task SessionId_AcceptsGuidShapeAndPreservesStorageKey(string sid)
    {
        Assert.True(ServerUtils.IsValidSessionId(sid));
        var store = new InMemoryClusterStore();
        var svc = NewService(store);
        await svc.SaveSessionAsync(sid, "face-token");
        var result = await svc.ChallengeAsync(new AttestationChallengeRequest
            { SessionId = sid, ClientId = ClientId, System = "android" });
        Assert.Equal(200, result.Status);
        var stored = await store.GetSessionAsync(sid);
        Assert.NotNull(stored);
        Assert.NotNull(stored.Value.Data["challengeHash"]);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("12345678123412341234123456789abc")]
    [InlineData("{12345678-1234-1234-1234-123456789abc}")]
    [InlineData("(12345678-1234-1234-1234-123456789abc)")]
    [InlineData(" 12345678-1234-1234-1234-123456789abc")]
    [InlineData("12345678-1234-1234-1234-123456789abc ")]
    [InlineData("12345678-1234-1234-1234-123456789abc\n")]
    [InlineData("12345678-1234-1234-1234-123456789abg")]
    [InlineData("12345678_1234-1234-1234-123456789abc")]
    public async Task SessionId_RejectsMalformedValuesAcrossHandlers(string? sid)
    {
        Assert.False(ServerUtils.IsValidSessionId(sid));
        var svc = NewService(new InMemoryClusterStore());
        var expected = string.IsNullOrEmpty(sid) ? "MISSING_SESSION_ID" : "INVALID_SESSION_ID";
        Assert.Equal(expected, (await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid })).Code);
        Assert.Equal(expected, (await svc.RegisterAsync(new AttestationRegisterRequest { SessionId = sid })).Code);
        Assert.Equal(expected, (await svc.VerifyAsync(new AttestationVerifyRequest { SessionId = sid })).Code);
        Assert.Equal(expected, (await svc.SessionTokenAsync(new SessionTokenRequest { SessionId = sid })).Code);
        Assert.Equal(expected, (await svc.LivenessDigestAsync(new LivenessDigestRequest { SessionId = sid })).Code);
    }

    [Fact]
    public async Task Challenge_IssuesHashAndBindsIdentity()
    {
        var store = new InMemoryClusterStore();
        var svc = NewService(store);
        var sid = Guid.NewGuid().ToString();
        await svc.SaveSessionAsync(sid, "face-token");

        var outcome = await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = ClientId, System = "android" });

        Assert.Equal(200, outcome.Status);
        var success = Assert.IsType<AttestationChallengeSuccess>(outcome.Body);
        Assert.Equal(64, success.ChallengeHash.Length);
        Assert.Equal(ClientId, success.ClientId);
        Assert.Equal("android", success.System);
    }

    [Fact]
    public async Task Challenge_RejectsSecondIssue()
    {
        var store = new InMemoryClusterStore();
        var svc = NewService(store);
        var sid = Guid.NewGuid().ToString();
        await svc.SaveSessionAsync(sid, "face-token");
        await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = ClientId, System = "android" });

        var second = await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = ClientId, System = "android" });

        Assert.Equal(409, second.Status);
        Assert.Equal("CHALLENGE_ALREADY_EXISTS", second.Code);
    }

    [Theory]
    [InlineData(null, ClientId, "android", "MISSING_SESSION_ID")]
    [InlineData("not-a-uuid", ClientId, "android", "INVALID_SESSION_ID")]
    public async Task Challenge_ValidatesInputs(string? sid, string? clientId, string? system, string expectedCode)
    {
        var svc = NewService(new InMemoryClusterStore());
        var outcome = await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = clientId, System = system });
        Assert.False(outcome.Ok);
        Assert.Equal(expectedCode, outcome.Code);
    }

    [Fact]
    public async Task Register_ReachesAttestationDispatch()
    {
        // The Android verifier is stubbed until P5, so a well-formed register
        // reaches the attestation step and fails there (proves the wiring).
        var store = new InMemoryClusterStore();
        var svc = NewService(store);
        var client = new TestAttestationClient();
        var sid = Guid.NewGuid().ToString();
        await svc.SaveSessionAsync(sid, "face-token");
        var challenge = await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = ClientId, System = "android" });
        var challengeHash = ((AttestationChallengeSuccess)challenge.Body).ChallengeHash;

        var payload = JsonSerializer.Serialize(new
        {
            challengeHash,
            encryptionPublicCert = client.EncCertPem,
            attestJson = "{}",
        });
        var outcome = await svc.RegisterAsync(new AttestationRegisterRequest
        {
            SessionId = sid,
            ClientId = ClientId,
            System = "android",
            Body = new AttestationRegisterBody { Payload = payload, AuthPublicCert = client.AuthCertPem, Signature = client.Sign(payload) },
        });

        Assert.Equal(401, outcome.Status);
        Assert.Equal("ATTESTATION_VERIFICATION_FAIL", outcome.Code);
    }

    [Fact]
    public async Task VerifyTokenDigest_HappyPath()
    {
        var store = new InMemoryClusterStore();
        var svc = NewService(store);
        var client = new TestAttestationClient();
        var sid = Guid.NewGuid().ToString();
        const string faceToken = "face-token-xyz";

        await svc.SaveSessionAsync(sid, faceToken);
        var challenge = await svc.ChallengeAsync(new AttestationChallengeRequest { SessionId = sid, ClientId = ClientId, System = "android" });
        var challengeHash = ((AttestationChallengeSuccess)challenge.Body).ChallengeHash;

        // Pre-register the auth cert (register's attestation step is covered in P5).
        var now = DateTime.UtcNow.ToString("o");
        await store.SetCertificateAsync(client.AuthThumbprint, new CertificateData
        {
            ClientId = ClientId,
            System = "android",
            Thumbprint = client.AuthThumbprint,
            PublicCert = client.AuthCertPem,
            CreatedAt = now,
            LastVerifiedAt = now,
        });

        // --- verify ---
        var verifyPayload = JsonSerializer.Serialize(new { challengeHash, encryptionPublicCert = client.EncCertPem });
        var verifyOutcome = await svc.VerifyAsync(new AttestationVerifyRequest
        {
            SessionId = sid,
            ClientId = ClientId,
            System = "android",
            Body = new AttestationVerifyBody { Payload = verifyPayload, AuthPublicCert = client.AuthCertPem, Signature = client.Sign(verifyPayload) },
        });
        Assert.Equal(200, verifyOutcome.Status);
        var verifyResult = Assert.IsType<AttestationVerifyResult>(verifyOutcome.Body);
        Assert.True(verifyResult.Exists);
        var serverEncPub = verifyResult.ServerEncryptionPublicKey!;

        // --- token ---
        var tokenInner = JsonSerializer.Serialize(new { challengeHash, clientId = ClientId, system = "android" });
        var tokenEncrypted = CryptoUtils.EncryptWithPublicKeyEC(tokenInner, serverEncPub)!;
        var tokenOutcome = await svc.SessionTokenAsync(new SessionTokenRequest
        {
            SessionId = sid,
            Body = new SessionTokenBody { EncryptedData = tokenEncrypted, Signature = client.Sign(tokenEncrypted) },
        });
        Assert.Equal(200, tokenOutcome.Status);
        var tokenSuccess = Assert.IsType<SessionTokenSuccess>(tokenOutcome.Body);
        var decryptedToken = CryptoUtils.DecryptWithPrivateKeyEC(tokenSuccess.EncryptedData, client.EncPrivateKeyPem)!;
        using (var doc = JsonDocument.Parse(decryptedToken))
        {
            Assert.Equal(faceToken, doc.RootElement.GetProperty("token").GetString());
        }

        // --- digest ---
        const string digestValue = "liveness-digest-hash";
        var digestInner = JsonSerializer.Serialize(new { cid = ClientId, os = "android", digest = digestValue });
        var digestEncrypted = CryptoUtils.EncryptWithPublicKeyEC(digestInner, serverEncPub)!;
        var digestOutcome = await svc.LivenessDigestAsync(new LivenessDigestRequest
        {
            SessionId = sid,
            Body = new LivenessDigestBody { EncryptedData = digestEncrypted, Signature = client.Sign(digestEncrypted) },
        });
        Assert.Equal(200, digestOutcome.Status);
        var digestData = Assert.IsType<LivenessDigestData>(digestOutcome.Data);
        Assert.Equal(digestValue, digestData.ClientDigest);

        // Host-facing liveness outcome reflects the submitted digest.
        var liveness = await svc.GetLivenessOutcomeAsync(sid);
        Assert.True(liveness.Completed);
        Assert.Equal(digestValue, liveness.ClientDigest);
    }
}
