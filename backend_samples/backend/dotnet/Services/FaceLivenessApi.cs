using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using FaceLivenessAttestationBackendSample.Configuration;
using Microsoft.Extensions.Options;

namespace FaceLivenessAttestationBackendSample.Services;

/// <summary>Thrown for a non-2xx response from the Face service.</summary>
public sealed class FaceApiException : Exception
{
    public int Status { get; }
    public string Body { get; }

    public FaceApiException(string message, int status, string body = "") : base(message)
    {
        Status = status;
        Body = body;
    }
}

/// <summary>Parsed create-session response.</summary>
public sealed class CreateSessionResult
{
    public string? SessionId { get; set; }
    public string? AuthToken { get; set; }
}

/// <summary>
/// Thin client over the Azure Face liveness-session REST endpoints. The Face
/// resource is validated and the host is pinned to
/// <c>*.cognitiveservices.azure.com</c> to prevent SSRF via a crafted resource.
/// </summary>
public sealed partial class FaceLivenessApi
{
    private static readonly JsonSerializerOptions Json = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };

    private readonly HttpClient _http;
    private readonly string _apiVersion;

    [GeneratedRegex("^[A-Za-z0-9-]+$")]
    private static partial Regex ResourceRegex();

    public FaceLivenessApi(HttpClient http, IOptions<AppSettings> settings)
    {
        _http = http;
        _apiVersion = settings.Value.FaceApiVersion;
    }

    /// <summary>
    /// Start a Face liveness session. With a verify image, a
    /// detectLivenessWithVerify session is created (multipart); otherwise a plain
    /// detectLiveness session.
    /// </summary>
    public async Task<CreateSessionResult> CreateSessionAsync(string resource, string apiKey, string mode, byte[]? verifyImage = null, string verifyImageName = "verify.jpg")
    {
        ValidateResource(resource);
        var action = verifyImage is null ? "detectLiveness" : "detectLivenessWithVerify";
        var endpoint = $"https://{resource}.cognitiveservices.azure.com/face/{_apiVersion}/{action}-sessions";

        using var request = new HttpRequestMessage(HttpMethod.Post, endpoint);
        request.Headers.Add("Ocp-Apim-Subscription-Key", apiKey);

        if (verifyImage is null)
        {
            var body = JsonSerializer.Serialize(new
            {
                livenessOperationMode = mode,
                enableSessionImage = true,
                deviceCorrelationIdSetInClient = true,
                deviceCorrelationIdSetInSessionStart = true,
            });
            request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }
        else
        {
            var form = new MultipartFormDataContent
            {
                { new StringContent(mode), "livenessOperationMode" },
                { new StringContent("true"), "enableSessionImage" },
                { new StringContent("true"), "deviceCorrelationIdSetInClient" },
                { new StringContent("true"), "deviceCorrelationIdSetInSessionStart" },
            };
            var image = new ByteArrayContent(verifyImage);
            image.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
            form.Add(image, "verifyImage", verifyImageName);
            request.Content = form;
        }

        using var response = await _http.SendAsync(request);
        var text = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new FaceApiException($"createSession failed: HTTP {(int)response.StatusCode}", (int)response.StatusCode, text);
        }
        return JsonSerializer.Deserialize<CreateSessionResult>(text, Json) ?? new CreateSessionResult();
    }

    /// <summary>Fetch the liveness session result JSON from the Face service.</summary>
    public async Task<string> QuerySessionResultAsync(string resource, string apiKey, string action, string sessionId)
    {
        ValidateResource(resource);
        var endpoint = $"https://{resource}.cognitiveservices.azure.com/face/{_apiVersion}/{action}-sessions/{sessionId}";

        using var request = new HttpRequestMessage(HttpMethod.Get, endpoint);
        request.Headers.Add("Ocp-Apim-Subscription-Key", apiKey);

        using var response = await _http.SendAsync(request);
        var text = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            throw new FaceApiException($"querySessionResult failed: HTTP {(int)response.StatusCode}", (int)response.StatusCode, text);
        }
        return text;
    }

    private static void ValidateResource(string resource)
    {
        if (string.IsNullOrEmpty(resource) || !ResourceRegex().IsMatch(resource))
        {
            throw new FaceApiException("Invalid Face resource name (letters, digits, hyphens only)", 400);
        }
    }
}
