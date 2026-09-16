namespace FaceLivenessAttestationBackendSample.Storage;

/// <summary>
/// App-owned session data (kept separate from the attestation library's session
/// record): the Face resource + key used to poll the liveness result. Never sent
/// to the browser.
/// </summary>
public sealed class AppSession
{
    public required string Resource { get; init; }
    public required string ApiKey { get; init; }
    /// <summary>"detectLiveness" or "detectLivenessWithVerify".</summary>
    public required string Action { get; init; }
}

/// <summary>Store for the app-owned <see cref="AppSession"/>, keyed by session id.</summary>
public interface IAppSessionStore
{
    Task<bool> SaveAsync(string sid, AppSession session);
    Task<AppSession?> GetAsync(string sid);
}
