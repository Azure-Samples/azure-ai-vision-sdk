namespace Azure.AI.Vision.Face.DeviceAttestation.Models;

/// <summary>Certificate summary embedded in an <see cref="AppAttestVerdict"/>.</summary>
public sealed class AppAttestCertInfo
{
    /// <summary>The certificate subject distinguished name.</summary>
    public required string Subject { get; init; }
    /// <summary>The certificate issuer distinguished name.</summary>
    public required string Issuer { get; init; }
    /// <summary>The certificate serial number.</summary>
    public required string SerialNumber { get; init; }
    /// <summary>The beginning of the certificate validity period.</summary>
    public required string ValidFrom { get; init; }
    /// <summary>The end of the certificate validity period.</summary>
    public required string ValidTo { get; init; }
    /// <summary>SHA-256 thumbprint (lowercase hex); present for credCert only.</summary>
    public string? Thumbprint { get; init; }
}

/// <summary>The assertion proof bundled alongside the attestation.</summary>
public sealed class AppAttestAssertionInfo
{
    /// <summary>The length of the assertion authenticator data, in bytes.</summary>
    public int AuthenticatorDataLength { get; init; }
    /// <summary>The assertion relying-party identifier hash, as lowercase hexadecimal.</summary>
    public required string RpIdHash { get; init; }
    /// <summary>The flags byte from the assertion authenticator data.</summary>
    public int Flags { get; init; }
    /// <summary>The signature counter from the assertion authenticator data.</summary>
    public long SignCount { get; init; }
    /// <summary>The length of the assertion signature, in bytes.</summary>
    public int SignatureLength { get; init; }
    /// <summary>"der" (Apple's documented format) or "ieee-p1363".</summary>
    public required string SignatureEncoding { get; init; }
    /// <summary>The expected assertion client-data hash, as lowercase hexadecimal.</summary>
    public required string ExpectedClientDataHash { get; init; }
    /// <summary>Whether the assertion signature was successfully verified.</summary>
    public bool SignatureVerified { get; init; }
}

/// <summary>
/// Decoded App Attest verdict — analog of the Play Integrity verdict. Persisted
/// under the certificate metadata so subsequent calls can re-derive the credCert
/// public key and verify assertions bound to those calls.
/// </summary>
public sealed class AppAttestVerdict
{
    /// <summary>The attestation statement format identifier.</summary>
    public required string Fmt { get; init; }
    /// <summary>lowercase hex</summary>
    public required string RpIdHash { get; init; }
    /// <summary>matched IOS_APP_ID (TeamID.BundleID)</summary>
    public required string AppId { get; init; }
    /// <summary>"appattest" (prod) or "appattestdevelop"</summary>
    public required string Aaguid { get; init; }
    /// <summary>The flags byte from the attestation authenticator data.</summary>
    public int Flags { get; init; }
    /// <summary>The signature counter from the attestation authenticator data.</summary>
    public long SignCount { get; init; }
    /// <summary>lowercase hex</summary>
    public required string CredentialId { get; init; }
    /// <summary>Whether the credential identifier matches the attested public key.</summary>
    public bool CredentialIdMatchesPubKey { get; init; }
    /// <summary>A summary of the attested credential certificate.</summary>
    public required AppAttestCertInfo CredCert { get; init; }
    /// <summary>credCert in PEM form, persisted for per-call assertions.</summary>
    public required string CredCertPem { get; init; }
    /// <summary>A summary of the intermediate certificate in the attestation chain.</summary>
    public required AppAttestCertInfo IntermediateCert { get; init; }
    /// <summary>The length of the App Attest receipt, in bytes.</summary>
    public int ReceiptLength { get; init; }
    /// <summary>Apple App Attest receipt (base64), or null. For the DeviceCheck fraud-risk exchange.</summary>
    public string? Receipt { get; init; }
    /// <summary>The length of the attestation authenticator data, in bytes.</summary>
    public int AuthDataLength { get; init; }
    /// <summary>nonce extension OCTET STRING value, lowercase hex</summary>
    public required string NonceExtension { get; init; }
    /// <summary>The clientDataHash expected for the attestation; lowercase hex</summary>
    public required string ExpectedClientDataHash { get; init; }
    /// <summary>Auth cert thumbprint used in the assertion binding.</summary>
    public required string AuthCertThumbprint { get; init; }
    /// <summary>Whether the attestation nonce was successfully verified.</summary>
    public bool NonceVerified { get; init; }
    /// <summary>Details of the assertion verified alongside the attestation.</summary>
    public required AppAttestAssertionInfo Assertion { get; init; }
    /// <summary>A description of the cryptographic bindings verified for this verdict.</summary>
    public required string ChallengeBinding { get; init; }
}
