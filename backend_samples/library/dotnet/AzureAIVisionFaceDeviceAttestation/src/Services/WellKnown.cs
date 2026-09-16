using System.Text.Json.Nodes;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;

namespace Azure.AI.Vision.Face.DeviceAttestation.Services;

/// <summary>
/// App Link / Universal Link binding documents served at /.well-known/*, driven
/// entirely by the injected <see cref="AttestationConfig"/> so the library binds
/// to any domain / app identity purely through configuration.
/// </summary>
internal static class WellKnown
{
    /// <summary>Build the AASA document iOS fetches from /.well-known to bind the domain.</summary>
    public static JsonObject AppleAppSiteAssociation(AttestationConfig cfg)
    {
        var appId = (!string.IsNullOrEmpty(cfg.IosApplinkAppId) ? cfg.IosApplinkAppId! : cfg.IosAppId).Trim();
        var clipId = (cfg.IosAppClipId ?? "").Trim();

        var appIds = new JsonArray();
        if (appId.Length > 0)
        {
            appIds.Add(appId);
        }
        if (clipId.Length > 0)
        {
            appIds.Add(clipId);
        }

        var doc = new JsonObject
        {
            ["applinks"] = new JsonObject
            {
                ["details"] = new JsonArray
                {
                    new JsonObject
                    {
                        ["appIDs"] = appIds,
                        ["components"] = new JsonArray
                        {
                            new JsonObject
                            {
                                ["/"] = cfg.ApplinkPath,
                                ["comment"] = "Opens the app for liveness sessions.",
                            },
                        },
                    },
                },
            },
        };

        if (clipId.Length > 0)
        {
            doc["appclips"] = new JsonObject { ["apps"] = new JsonArray { clipId } };
        }
        return doc;
    }

    /// <summary>Build the Digital Asset Links document Android fetches to bind the domain.</summary>
    public static JsonArray AssetLinks(AttestationConfig cfg)
    {
        var packageName = (cfg.AndroidPackageName ?? "").Trim();

        var fingerprints = new JsonArray();
        foreach (var fp in cfg.AndroidSha256CertFingerprints)
        {
            fingerprints.Add(fp);
        }

        return new JsonArray
        {
            new JsonObject
            {
                ["relation"] = new JsonArray { "delegate_permission/common.handle_all_urls" },
                ["target"] = new JsonObject
                {
                    ["namespace"] = "android_app",
                    ["package_name"] = packageName,
                    ["sha256_cert_fingerprints"] = fingerprints,
                },
            },
        };
    }
}
