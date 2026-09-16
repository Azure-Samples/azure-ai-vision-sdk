using Azure.AI.Vision.Face.DeviceAttestation.Handlers;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation;

public sealed partial class AttestationService
{
    /// <summary>POST /api/attestation/challenge</summary>
    public Task<HandlerOutcome> ChallengeAsync(AttestationChallengeRequest req)
        => WithStorageFailureAsync(() => new ChallengeHandler(_ctx).HandleAsync(req));

    /// <summary>POST /api/attestation/register</summary>
    public Task<HandlerOutcome> RegisterAsync(AttestationRegisterRequest req)
        => WithStorageFailureAsync(() => new RegisterHandler(_ctx).HandleAsync(req));

    /// <summary>POST /api/attestation/verify</summary>
    public Task<HandlerOutcome> VerifyAsync(AttestationVerifyRequest req)
        => WithStorageFailureAsync(() => new VerifyHandler(_ctx).HandleAsync(req));

    /// <summary>POST /api/session/token</summary>
    public Task<HandlerOutcome> SessionTokenAsync(SessionTokenRequest req)
        => WithStorageFailureAsync(() => new TokenHandler(_ctx).HandleAsync(req));

    /// <summary>POST /api/liveness/digest</summary>
    public Task<HandlerOutcome> LivenessDigestAsync(LivenessDigestRequest req)
        => WithStorageFailureAsync(() => new DigestHandler(_ctx).HandleAsync(req));

    private async Task<HandlerOutcome> WithStorageFailureAsync(Func<Task<HandlerOutcome>> action)
    {
        try { return await action(); }
        catch (StorageException)
        {
            return Outcomes.Fail(_ctx.Logger, "attestation", 503, "STORAGE_UNAVAILABLE", "Attestation storage unavailable");
        }
    }
}
