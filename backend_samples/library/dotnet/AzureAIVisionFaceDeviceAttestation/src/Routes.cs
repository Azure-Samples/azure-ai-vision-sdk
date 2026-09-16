namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>Canonical route paths, for use as telemetry tags in the host.</summary>
public static class Routes
{
    /// <summary>The route that creates and binds an attestation challenge.</summary>
    public const string Challenge = "attestation/challenge";
    /// <summary>The route that registers a verified device attestation.</summary>
    public const string Register = "attestation/register";
    /// <summary>The route that verifies a registered attestation certificate.</summary>
    public const string Verify = "attestation/verify";
    /// <summary>The route that exchanges an encrypted session token.</summary>
    public const string SessionToken = "session/token";
    /// <summary>The route that validates an encrypted liveness digest.</summary>
    public const string LivenessDigest = "liveness/digest";
}
