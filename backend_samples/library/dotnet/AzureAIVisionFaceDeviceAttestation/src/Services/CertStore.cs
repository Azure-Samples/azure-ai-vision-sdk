using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Crypto;
using Azure.AI.Vision.Face.DeviceAttestation.Storage;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>Result of saving a certificate: its thumbprint and whether it was newly created.</summary>
internal sealed record SaveCertificateResult(string Thumbprint, bool IsNew);

/// <summary>
/// Certificate store, keyed by the SHA-256 thumbprint of the cert DER.
/// Persistence, key namespace, and TTL are delegated to the injected
/// <see cref="IClusterStore"/>; this layer keeps the domain concerns
/// (thumbprint computation, domain events, metadata merge).
/// </summary>
internal static class CertStore
{
    /// <summary>
    /// Save a certificate: create it on first sight, or bump its
    /// <c>lastVerifiedAt</c> on subsequent calls. Returns the thumbprint + a new
    /// flag, or null on error.
    /// </summary>
    public static async Task<SaveCertificateResult?> SaveCertificateAsync(
        AttestationContext ctx,
        string clientId,
        string system,
        string publicCert,
        JsonObject? metadata)
    {
        var thumbprint = CertUtils.ComputeCertThumbprint(publicCert);
        if (thumbprint is null)
        {
            ctx.Logger.TrackEvent("CertStore.SaveFail", new Dictionary<string, object?>
            {
                ["reason"] = "THUMBPRINT_COMPUTE_FAIL",
                ["clientId"] = clientId,
                ["system"] = system,
            });
            return null;
        }

        var existing = await ctx.Store.GetCertificateAsync(thumbprint);
        if (existing is not null)
        {
            if (existing.Value.ClientId != clientId || existing.Value.System != system) return null;
            existing.Value.LastVerifiedAt = IsoTime.Now();
            if (await ctx.Store.UpdateCertificateAsync(thumbprint, existing.Version, existing.Value) != UpdateResult.Applied)
            {
                return null;
            }
            ctx.Logger.TrackEvent("CertStore.Updated", new Dictionary<string, object?>
            {
                ["thumbprint"] = thumbprint,
                ["clientId"] = clientId,
                ["system"] = system,
                ["isNew"] = false,
            });
            return new SaveCertificateResult(thumbprint, false);
        }

        var now = IsoTime.Now();
        var certData = new CertificateData
        {
            ClientId = clientId,
            System = system,
            Thumbprint = thumbprint,
            PublicCert = publicCert,
            CreatedAt = now,
            LastVerifiedAt = now,
            Metadata = metadata,
        };
        if (!await ctx.Store.SetCertificateAsync(thumbprint, certData))
        {
            return null;
        }
        ctx.Logger.TrackEvent("CertStore.Created", new Dictionary<string, object?>
        {
            ["thumbprint"] = thumbprint,
            ["clientId"] = clientId,
            ["system"] = system,
            ["isNew"] = true,
        });
        return new SaveCertificateResult(thumbprint, true);
    }

    /// <summary>Load a certificate record by thumbprint.</summary>
    public static Task<Snapshot<CertificateData>?> GetCertificateAsync(AttestationContext ctx, string thumbprint)
        => ctx.Store.GetCertificateAsync(thumbprint);

    /// <summary>
    /// Merge fields into a cert record's metadata and bump <c>lastVerifiedAt</c>,
    /// preserving the existing TTL. Used to advance the assertion sign-count.
    /// Returns false if the key is gone/expired.
    /// </summary>
    public static async Task<bool> UpdateCertificateMetadataAsync(
        AttestationContext ctx,
        string thumbprint,
        JsonObject partialMetadata,
        Snapshot<CertificateData> snapshot)
    {
        var existing = snapshot.Value;

        existing.LastVerifiedAt = IsoTime.Now();
        var merged = existing.Metadata ?? new JsonObject();
        foreach (var kv in partialMetadata)
        {
            merged[kv.Key] = kv.Value?.DeepClone();
        }
        existing.Metadata = merged;

        return await ctx.Store.UpdateCertificateAsync(thumbprint, snapshot.Version, existing) == UpdateResult.Applied;
    }
}
