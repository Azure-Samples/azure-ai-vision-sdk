using System.Text.Json;
using System.Text.Json.Serialization;

namespace FaceLivenessDetectorSample.Shared.Models;

public class GenerateTokenResponse
{
    [JsonPropertyName("sessionData")]
    public JsonElement? SessionData { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("error")]
    public ErrorInfo? Error { get; set; }
}

public class ErrorInfo
{
    [JsonPropertyName("token")]
    public string? Token { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }
}
