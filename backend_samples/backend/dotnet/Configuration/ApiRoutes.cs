namespace FaceLivenessAttestationBackendSample.Configuration;

/// <summary>
/// The HTTP surface the host exposes — paths and query-parameter names — bound
/// from the "ApiRoutes" configuration section. These are entirely the host's
/// choice: the attestation library binds no paths, so a consumer can mount the
/// endpoints anywhere (or behind an API gateway) just by changing config.
/// Defaults mirror the React sample.
/// </summary>
public sealed class ApiRoutes
{
    public string Challenge { get; set; } = "/api/attestation/challenge";
    public string Register { get; set; } = "/api/attestation/register";
    public string Verify { get; set; } = "/api/attestation/verify";
    public string SessionToken { get; set; } = "/api/session/token";
    public string SessionResult { get; set; } = "/api/session/result";
    public string LivenessDigest { get; set; } = "/api/liveness/digest";
    public string Healthz { get; set; } = "/healthz";

    // /.well-known documents (served at the standard root paths).
    public string AppleAppSiteAssociation { get; set; } = "/.well-known/apple-app-site-association";
    public string AssetLinks { get; set; } = "/.well-known/assetlinks.json";

    // Query-parameter names the client uses (also the host's choice).
    public string SessionIdParam { get; set; } = "s";
    public string ClientIdParam { get; set; } = "cid";
    public string SystemParam { get; set; } = "sys";
}
