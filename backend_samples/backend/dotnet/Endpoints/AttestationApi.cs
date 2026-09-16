using System.Text.Json;
using System.Text.Json.Serialization;
using Azure.AI.Vision.Face.DeviceAttestation;
using Azure.AI.Vision.Face.DeviceAttestation.Handlers;
using Azure.AI.Vision.Face.DeviceAttestation.Models;
using FaceLivenessAttestationBackendSample.Configuration;
using FaceLivenessAttestationBackendSample.Services;
using FaceLivenessAttestationBackendSample.Storage;
using Microsoft.Extensions.Options;

namespace FaceLivenessAttestationBackendSample.Endpoints;

/// <summary>
/// Maps the HTTP surface onto the transport-agnostic <see cref="AttestationService"/>.
/// Paths and query-parameter names come from <see cref="ApiRoutes"/> (config) —
/// the library binds no routes, so a host can mount these anywhere.
/// </summary>
public static class AttestationApi
{
    // camelCase + omit nulls, matching the wire contract the mobile clients expect.
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public static void MapAttestationApi(this WebApplication app)
    {
        var r = app.Services.GetRequiredService<IOptions<ApiRoutes>>().Value;

        app.MapPost(r.Challenge, async (HttpRequest http, AttestationService svc) =>
        {
            var outcome = await svc.ChallengeAsync(new AttestationChallengeRequest
            {
                SessionId = http.Query[r.SessionIdParam],
                ClientId = http.Query[r.ClientIdParam],
                System = http.Query[r.SystemParam],
            });
            return Serialize(outcome);
        });

        app.MapPost(r.Register, async (HttpRequest http, AttestationService svc) =>
        {
            var outcome = await svc.RegisterAsync(new AttestationRegisterRequest
            {
                SessionId = http.Query[r.SessionIdParam],
                ClientId = http.Query[r.ClientIdParam],
                System = http.Query[r.SystemParam],
                Body = await ReadBodyAsync<AttestationRegisterBody>(http),
            });
            return Serialize(outcome);
        });

        app.MapPost(r.Verify, async (HttpRequest http, AttestationService svc) =>
        {
            var outcome = await svc.VerifyAsync(new AttestationVerifyRequest
            {
                SessionId = http.Query[r.SessionIdParam],
                ClientId = http.Query[r.ClientIdParam],
                System = http.Query[r.SystemParam],
                Body = await ReadBodyAsync<AttestationVerifyBody>(http),
            });
            return Serialize(outcome);
        });

        app.MapPost(r.SessionToken, async (HttpRequest http, AttestationService svc) =>
        {
            var outcome = await svc.SessionTokenAsync(new SessionTokenRequest
            {
                SessionId = http.Query[r.SessionIdParam],
                Body = await ReadBodyAsync<SessionTokenBody>(http),
            });
            return Serialize(outcome);
        });

        app.MapPost(r.LivenessDigest, async (HttpRequest http, AttestationService svc) =>
        {
            var outcome = await svc.LivenessDigestAsync(new LivenessDigestRequest
            {
                SessionId = http.Query[r.SessionIdParam],
                Body = await ReadBodyAsync<LivenessDigestBody>(http),
            });
            return Serialize(outcome);
        });

        // GET session result: the host owns polling (Face resource/key are its own,
        // never in the library) — reads the app session + the library's completion signal.
        app.MapGet(r.SessionResult, async (HttpRequest http, AttestationService svc, IAppSessionStore appSessions, FaceLivenessApi face) =>
        {
            string? sid = http.Query[r.SessionIdParam];
            if (string.IsNullOrEmpty(sid))
            {
                return Results.Json(new { message = "Missing session ID" }, Json, statusCode: 400);
            }
            var appSession = await appSessions.GetAsync(sid);
            if (appSession is null)
            {
                return Results.Json(new { status = "notfound" }, Json, statusCode: 404);
            }
            if (string.IsNullOrWhiteSpace(appSession.Resource) || string.IsNullOrWhiteSpace(appSession.ApiKey))
            {
                return Results.Json(new { status = "error", message = "Session has no query credentials" }, Json, statusCode: 409);
            }

            var outcome = await svc.GetLivenessOutcomeAsync(sid);
            if (!outcome.Completed)
            {
                return Results.Json(new { status = "pending" }, Json, statusCode: 200);
            }

            try
            {
                var resultJson = await face.QuerySessionResultAsync(appSession.Resource, appSession.ApiKey, appSession.Action, sid);
                using var doc = JsonDocument.Parse(resultJson);
                if (!HasLivenessDecision(doc.RootElement, out var attempt))
                {
                    return Results.Json(new { status = "pending" }, Json, statusCode: 200);
                }
                if (string.IsNullOrWhiteSpace(outcome.ClientDigest)
                    || !attempt.TryGetProperty("digest", out var serviceDigest)
                    || serviceDigest.ValueKind != JsonValueKind.String
                    || !string.Equals(outcome.ClientDigest, serviceDigest.GetString(), StringComparison.Ordinal))
                {
                    return Results.Json(new { status = "error", code = "DIGEST_MISMATCH", message = "Liveness result digest validation failed" }, Json, statusCode: 409);
                }
                return Results.Json(new { status = "done", clientDigest = outcome.ClientDigest, result = doc.RootElement.Clone() }, Json, statusCode: 200);
            }
            catch (FaceApiException e)
            {
                return Results.Json(new { status = "error", message = e.Message }, Json, statusCode: 502);
            }
        });

        app.MapGet(r.AppleAppSiteAssociation, (AttestationService svc) => Results.Json(svc.AppleAppSiteAssociation(), contentType: "application/json"));
        app.MapGet(r.AssetLinks, (AttestationService svc) => Results.Json(svc.AndroidAssetLinks(), contentType: "application/json"));
        app.MapGet(r.Healthz, () => Results.Json(new { status = "ok" }));
    }

    private static IResult Serialize(HandlerOutcome outcome)
        => Results.Json(outcome.Body, Json, statusCode: outcome.Status);

    private static async Task<T?> ReadBodyAsync<T>(HttpRequest http) where T : class
    {
        try
        {
            return await http.ReadFromJsonAsync<T>(Json);
        }
        catch
        {
            return null;
        }
    }

    private static bool HasLivenessDecision(JsonElement root, out JsonElement result)
    {
        result = default;
        if (root.ValueKind != JsonValueKind.Object
            || !root.TryGetProperty("results", out var results)
            || results.ValueKind != JsonValueKind.Object
            || !results.TryGetProperty("attempts", out var attempts)
            || attempts.ValueKind != JsonValueKind.Array)
        {
            return false;
        }

        var enumerator = attempts.EnumerateArray();
        if (!enumerator.MoveNext()
            || enumerator.Current.ValueKind != JsonValueKind.Object
            || !enumerator.Current.TryGetProperty("result", out result)
            || result.ValueKind != JsonValueKind.Object
            || !result.TryGetProperty("livenessDecision", out var decision)
            || decision.ValueKind is JsonValueKind.Null or JsonValueKind.Undefined)
        {
            return false;
        }

        return decision.ValueKind != JsonValueKind.String
            || !string.IsNullOrEmpty(decision.GetString());
    }
}
