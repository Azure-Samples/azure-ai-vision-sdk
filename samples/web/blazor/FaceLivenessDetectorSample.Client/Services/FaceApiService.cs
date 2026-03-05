using System.Net.Http.Json;
using System.Text.Json;

namespace FaceLivenessDetectorSample.Client.Services;

public class FaceApiService
{
    private readonly HttpClient _httpClient;

    public FaceApiService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    /// <summary>
    /// Calls the backend to generate a Face Liveness session access token.
    /// </summary>
    public async Task<JsonElement> GenerateAccessTokenAsync(
        string action,
        string livenessOperationMode,
        string deviceCorrelationId,
        string userCorrelationId,
        byte[]? verifyImageBytes = null,
        string? verifyImageFileName = null)
    {
        using var content = new MultipartFormDataContent();
        content.Add(new StringContent(action), "Action");

        var parameters = JsonSerializer.Serialize(new
        {
            livenessOperationMode,
            deviceCorrelationId,
            userCorrelationId
        });
        content.Add(new StringContent(parameters), "parameters");

        if (action == "detectLivenessWithVerify" && verifyImageBytes != null)
        {
            var imageContent = new ByteArrayContent(verifyImageBytes);
            content.Add(imageContent, "verifyImage", verifyImageFileName ?? "image.jpg");
        }

        var response = await _httpClient.PostAsync("/api/generateAccessToken", content);
        var json = await response.Content.ReadFromJsonAsync<JsonElement>();
        return json;
    }

    /// <summary>
    /// Calls the backend to retrieve the session result.
    /// </summary>
    public async Task<JsonElement> GetSessionResultAsync(string action, string sessionId)
    {
        var response = await _httpClient.GetAsync($"/api/getSessionResult?action={action}&sessionId={sessionId}");
        var json = await response.Content.ReadFromJsonAsync<JsonElement>();
        return json;
    }
}
