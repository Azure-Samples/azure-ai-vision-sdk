using System.Linq;
using Azure.AI.Vision.Face.DeviceAttestation.Configuration;
using Azure.AI.Vision.Face.DeviceAttestation.Tests.Fakes;
using Xunit;

namespace Azure.AI.Vision.Face.DeviceAttestation.Tests;

public class WellKnownTests
{
    [Fact]
    public void AppleAppSiteAssociation_IncludesAppIdAndClip()
    {
        var cfg = new AttestationConfig
        {
            IosAppId = "TEAMID123.com.example.app",
            IosAppClipId = "TEAMID123.com.example.app.Clip",
            ApplinkPath = "/native*",
        };
        var svc = AttestationService.Create(cfg, new InMemoryClusterStore());

        var aasa = svc.AppleAppSiteAssociation();

        var detail = aasa["applinks"]!["details"]!.AsArray()[0]!;
        var appIds = detail["appIDs"]!.AsArray().Select(n => n!.GetValue<string>()).ToList();
        Assert.Contains("TEAMID123.com.example.app", appIds);
        Assert.Contains("TEAMID123.com.example.app.Clip", appIds);
        Assert.Equal("/native*", detail["components"]!.AsArray()[0]!["/"]!.GetValue<string>());
        Assert.Equal("TEAMID123.com.example.app.Clip", aasa["appclips"]!["apps"]!.AsArray()[0]!.GetValue<string>());
    }

    [Fact]
    public void AppleAppSiteAssociation_FallsBackToIosAppId_AndOmitsAppclips()
    {
        var cfg = new AttestationConfig { IosAppId = "TEAMID123.com.example.app" };
        var svc = AttestationService.Create(cfg, new InMemoryClusterStore());

        var aasa = svc.AppleAppSiteAssociation();

        Assert.Null(aasa["appclips"]);
        var appIds = aasa["applinks"]!["details"]!.AsArray()[0]!["appIDs"]!.AsArray();
        Assert.Single(appIds);
    }

    [Fact]
    public void AssetLinks_ContainsPackageAndFingerprints()
    {
        var cfg = new AttestationConfig
        {
            AndroidPackageName = "com.example.app",
            AndroidSha256CertFingerprints = new[] { "AA:BB", "CC:DD" },
        };
        var svc = AttestationService.Create(cfg, new InMemoryClusterStore());

        var links = svc.AndroidAssetLinks();

        var target = links[0]!["target"]!;
        Assert.Equal("android_app", target["namespace"]!.GetValue<string>());
        Assert.Equal("com.example.app", target["package_name"]!.GetValue<string>());
        Assert.Equal(2, target["sha256_cert_fingerprints"]!.AsArray().Count);
    }
}
