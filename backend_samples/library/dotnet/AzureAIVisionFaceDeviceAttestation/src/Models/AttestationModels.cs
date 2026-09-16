namespace Azure.AI.Vision.Face.DeviceAttestation.Models;

// --- attestation/challenge ---

/// <summary>Query inputs for the challenge endpoint (built by the host route).</summary>
public sealed class AttestationChallengeRequest
{
    /// <summary>The liveness session identifier.</summary>
    public string? SessionId { get; init; }
    /// <summary>The client identifier to bind to the challenge.</summary>
    public string? ClientId { get; init; }
    /// <summary>The client platform, either <c>ios</c> or <c>android</c>.</summary>
    public string? System { get; init; }
}

/// <summary>Success body: the freshly issued challenge, echoing the bound identity.</summary>
public sealed class AttestationChallengeSuccess
{
    /// <summary>The newly generated challenge, as lowercase hexadecimal.</summary>
    public required string ChallengeHash { get; init; }
    /// <summary>The client identifier bound to the challenge.</summary>
    public required string ClientId { get; init; }
    /// <summary>The client platform bound to the challenge.</summary>
    public required string System { get; init; }
}

// --- attestation/register ---

/// <summary>JSON body accepted by the register endpoint.</summary>
public sealed class AttestationRegisterBody
{
    /// <summary>The signed JSON payload containing the challenge, encryption certificate, and platform attestation.</summary>
    public string? Payload { get; init; }
    /// <summary>The client's authentication certificate in PEM format.</summary>
    public string? AuthPublicCert { get; init; }
    /// <summary>The base64-encoded signature of <see cref="Payload"/>.</summary>
    public string? Signature { get; init; }
}

/// <summary>Query + parsed body inputs for the register endpoint (built by the host route).</summary>
public sealed class AttestationRegisterRequest
{
    /// <summary>The liveness session identifier.</summary>
    public string? SessionId { get; init; }
    /// <summary>The client identifier bound to the session.</summary>
    public string? ClientId { get; init; }
    /// <summary>The client platform, either <c>ios</c> or <c>android</c>.</summary>
    public string? System { get; init; }
    /// <summary>Parsed JSON body, or null when the body was absent / not valid JSON.</summary>
    public AttestationRegisterBody? Body { get; init; }
}

/// <summary>Success body: acknowledgement + the server's encryption public key.</summary>
public sealed class AttestationRegisterSuccess
{
    /// <summary>A human-readable registration acknowledgement.</summary>
    public required string Message { get; init; }
    /// <summary>The server's encryption public key.</summary>
    public required string ServerEncryptionPublicKey { get; init; }
}

/// <summary>Android Key Attestation chain summary returned alongside a register.</summary>
public sealed class AndroidKeyAttestationInfo
{
    /// <summary>The number of certificates in the verified attestation chain.</summary>
    public int? ChainLength { get; init; }
    /// <summary>The name of the trusted root certificate authority.</summary>
    public string? RootCA { get; init; }
    /// <summary>A non-fatal warning about the leaf certificate validity period, if any.</summary>
    public string? LeafCertValidityWarning { get; init; }
}

/// <summary>
/// Host-facing attestation details returned alongside a successful register, as
/// <c>outcome.Data</c> (separate from the client body). Populated per platform.
/// </summary>
public sealed class AttestationRegisterData
{
    /// <summary>"ios" or "android".</summary>
    public required string Platform { get; init; }
    /// <summary>Non-fatal verification warnings, if any.</summary>
    public IReadOnlyList<string>? Warnings { get; init; }
    /// <summary>Android: Key Attestation chain result (verified to the pinned Google root).</summary>
    public AndroidKeyAttestationInfo? AndroidKeyAttestation { get; init; }
    /// <summary>Android: decoded Play Integrity verdict.</summary>
    public object? IntegrityVerdict { get; init; }
    /// <summary>iOS: full App Attest verdict (credCert details + the receipt).</summary>
    public object? AppAttestVerdict { get; init; }
}

// --- attestation/verify ---

/// <summary>JSON body accepted by the verify endpoint.</summary>
public sealed class AttestationVerifyBody
{
    /// <summary>The signed JSON payload containing the verification data.</summary>
    public string? Payload { get; init; }
    /// <summary>The client's authentication certificate in PEM format.</summary>
    public string? AuthPublicCert { get; init; }
    /// <summary>The base64-encoded signature of <see cref="Payload"/>.</summary>
    public string? Signature { get; init; }
    /// <summary>Top-level base64 CBOR assertion (iOS only), required once registered.</summary>
    public string? Assertion { get; init; }
}

/// <summary>Query + parsed body inputs for the verify endpoint (built by the host route).</summary>
public sealed class AttestationVerifyRequest
{
    /// <summary>The liveness session identifier.</summary>
    public string? SessionId { get; init; }
    /// <summary>The client identifier bound to the session.</summary>
    public string? ClientId { get; init; }
    /// <summary>The client platform, either <c>ios</c> or <c>android</c>.</summary>
    public string? System { get; init; }
    /// <summary>The parsed verification request body.</summary>
    public AttestationVerifyBody? Body { get; init; }
}

/// <summary>Success body: whether the cert exists and, if so, the server's enc public key.</summary>
public sealed class AttestationVerifyResult
{
    /// <summary>Whether the supplied authentication certificate is registered.</summary>
    public required bool Exists { get; init; }
    /// <summary>The server's encryption public key when the certificate exists.</summary>
    public string? ServerEncryptionPublicKey { get; init; }
}

// --- session/token ---

/// <summary>JSON body accepted by the token endpoint. <c>Assertion</c> is required on iOS.</summary>
public sealed class SessionTokenBody
{
    /// <summary>The base64-encoded request data encrypted for the server.</summary>
    public string? EncryptedData { get; init; }
    /// <summary>The base64-encoded signature of <see cref="EncryptedData"/>.</summary>
    public string? Signature { get; init; }
    /// <summary>The base64-encoded App Attest assertion for an iOS request.</summary>
    public string? Assertion { get; init; }
}

/// <summary>Query + parsed body inputs for the token endpoint (built by the host route).</summary>
public sealed class SessionTokenRequest
{
    /// <summary>The liveness session identifier.</summary>
    public string? SessionId { get; init; }
    /// <summary>The parsed token request body.</summary>
    public SessionTokenBody? Body { get; init; }
}

/// <summary>Success body: the Tink ECIES blob (base64) carrying the encrypted response.</summary>
public sealed class SessionTokenSuccess
{
    /// <summary>The base64-encoded response data encrypted for the client.</summary>
    public required string EncryptedData { get; init; }
}

// --- liveness/digest ---

/// <summary>JSON body accepted by the digest endpoint. <c>Assertion</c> is required on iOS.</summary>
public sealed class LivenessDigestBody
{
    /// <summary>The base64-encoded request data encrypted for the server.</summary>
    public string? EncryptedData { get; init; }
    /// <summary>The base64-encoded signature of <see cref="EncryptedData"/>.</summary>
    public string? Signature { get; init; }
    /// <summary>The base64-encoded App Attest assertion for an iOS request.</summary>
    public string? Assertion { get; init; }
}

/// <summary>Query + parsed body inputs for the digest endpoint (built by the host route).</summary>
public sealed class LivenessDigestRequest
{
    /// <summary>The liveness session identifier.</summary>
    public string? SessionId { get; init; }
    /// <summary>The parsed digest request body.</summary>
    public LivenessDigestBody? Body { get; init; }
}

/// <summary>Success body: the Tink ECIES blob (base64) carrying the encrypted ack.</summary>
public sealed class LivenessDigestSuccess
{
    /// <summary>The base64-encoded acknowledgement encrypted for the client.</summary>
    public required string EncryptedData { get; init; }
}

/// <summary>Host-facing digest result exposed as <c>outcome.Data</c>.</summary>
public sealed class LivenessDigestData
{
    /// <summary>The validated digest supplied by the client.</summary>
    public required string ClientDigest { get; init; }
}
