using Azure.AI.Vision.Face.DeviceAttestation.Services;

namespace Azure.AI.Vision.Face.DeviceAttestation.Android;

/// <summary>
/// Android Play Integrity + Key Attestation verifier — the seam the register
/// handler depends on. Delegates to <see cref="AndroidVerification"/>.
/// </summary>
internal static class AndroidVerifier
{
    /// <summary>Verify Android Key Attestation + Play Integrity.</summary>
    public static Task<AuthVerificationResult> VerifyAsync(AttestationContext ctx, AttestationMessageData messageData, string attestJson)
        => AndroidVerification.VerifyAsync(ctx.Config, ctx.Logger, messageData, attestJson);
}
