using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;

namespace FaceLivenessDetectorSample.Server.Controllers;

[ApiController]
[Route("api")]
public class FaceSessionController : ControllerBase
{
    private readonly IConfiguration _configuration;
    private readonly IHttpClientFactory _httpClientFactory;

    public FaceSessionController(IConfiguration configuration, IHttpClientFactory httpClientFactory)
    {
        _configuration = configuration;
        _httpClientFactory = httpClientFactory;
    }

    /// <summary>
    /// Generates an access token for face liveness detection.
    /// Mirrors: samples/web/nextjs/app/api/generateAccessToken/route.ts
    /// </summary>
    [HttpPost("generateAccessToken")]
    public async Task<IActionResult> GenerateAccessToken()
    {
        var form = await Request.ReadFormAsync();
        var paramString = form["parameters"].FirstOrDefault();
        var action = form["Action"].FirstOrDefault();
        var verifyImage = form.Files.GetFile("verifyImage");

        if (string.IsNullOrEmpty(paramString))
        {
            return BadRequest(new { message = "Parameters not formatted correctly", token = (string?)null });
        }

        var parameters = JsonSerializer.Deserialize<JsonElement>(paramString);
        var livenessOperationMode = parameters.GetProperty("livenessOperationMode").GetString();
        var deviceCorrelationId = parameters.GetProperty("deviceCorrelationId").GetString();
        var userCorrelationId = parameters.GetProperty("userCorrelationId").GetString();

        if (verifyImage == null && action == "detectLivenessWithVerify")
        {
            return BadRequest(new { message = "VerifyImage not provided for detectLivenessWithVerify", token = (string?)null });
        }

        if (action != "detectLiveness" && action != "detectLivenessWithVerify")
        {
            return BadRequest(new { message = "action parameter not expected", token = (string?)null });
        }

        if (livenessOperationMode != "Passive" && livenessOperationMode != "PassiveActive")
        {
            return BadRequest(new { message = "livenessOperationMode parameter not expected", token = (string?)null });
        }

        if (string.IsNullOrEmpty(deviceCorrelationId))
        {
            return BadRequest(new { message = "deviceCorrelationId parameter not expected", token = (string?)null });
        }

        if (string.IsNullOrEmpty(userCorrelationId))
        {
            return BadRequest(new { message = "userCorrelationId parameter not expected", token = (string?)null });
        }

        var faceEndpoint = _configuration["FaceApi:Endpoint"]!.TrimEnd('/');
        var faceKey = _configuration["FaceApi:Key"]!;

        try
        {
            var client = _httpClientFactory.CreateClient();
            client.DefaultRequestHeaders.Add("Ocp-Apim-Subscription-Key", faceKey);
            client.DefaultRequestHeaders.Add("X-MS-AZSDK-Telemetry", "sample=blazor-face-web-sdk");

            var url = $"{faceEndpoint}/face/v1.3-preview.1/{action}-sessions";
            HttpResponseMessage response;

            if (action == "detectLivenessWithVerify" && verifyImage != null)
            {
                // Multipart form for verify sessions
                using var content = new MultipartFormDataContent();
                using var imageStream = verifyImage.OpenReadStream();
                var imageContent = new StreamContent(imageStream);
                content.Add(imageContent, "verifyImage", verifyImage.FileName);
                content.Add(new StringContent(livenessOperationMode!), "livenessOperationMode");
                content.Add(new StringContent(deviceCorrelationId!), "deviceCorrelationId");
                content.Add(new StringContent(userCorrelationId!), "userCorrelationId");
                content.Add(new StringContent("true"), "enableSessionImage");

                response = await client.PostAsync(url, content);
            }
            else
            {
                // JSON body for liveness-only sessions
                var body = JsonSerializer.Serialize(new
                {
                    livenessOperationMode,
                    deviceCorrelationId,
                    userCorrelationId,
                    enableSessionImage = true
                });
                var jsonContent = new StringContent(body, Encoding.UTF8, "application/json");
                response = await client.PostAsync(url, jsonContent);
            }

            var responseBody = await response.Content.ReadAsStringAsync();
            var sessions = JsonSerializer.Deserialize<JsonElement>(responseBody);

            if (!response.IsSuccessStatusCode)
            {
                var errorMessage = "Unknown error";
                if (sessions.TryGetProperty("error", out var errorObj) &&
                    errorObj.TryGetProperty("message", out var msgProp))
                {
                    errorMessage = msgProp.GetString() ?? errorMessage;
                }
                return BadRequest(new { error = new { token = (string?)null, message = errorMessage } });
            }

            return Ok(new { sessionData = sessions, message = "success" });
        }
        catch (Exception ex)
        {
            return BadRequest(new { error = new { token = (string?)null, message = ex.Message } });
        }
    }

    /// <summary>
    /// Retrieves session results for a completed liveness detection session.
    /// Mirrors: samples/web/nextjs/app/api/getSessionResult/route.ts
    /// </summary>
    [HttpGet("getSessionResult")]
    public async Task<IActionResult> GetSessionResult(
        [FromQuery] string action,
        [FromQuery] string sessionId)
    {
        var faceEndpoint = _configuration["FaceApi:Endpoint"]!.TrimEnd('/');
        var faceKey = _configuration["FaceApi:Key"]!;

        try
        {
            var client = _httpClientFactory.CreateClient();
            client.DefaultRequestHeaders.Add("Ocp-Apim-Subscription-Key", faceKey);
            client.DefaultRequestHeaders.Add("X-MS-AZSDK-Telemetry", "sample=blazor-face-web-sdk");
            client.DefaultRequestHeaders.Add("Accept", "application/json");

            var url = $"{faceEndpoint}/face/v1.3-preview.1/{action}-sessions/{sessionId}";
            var response = await client.GetAsync(url);
            var responseBody = await response.Content.ReadAsStringAsync();
            var result = JsonSerializer.Deserialize<JsonElement>(responseBody);

            if (!response.IsSuccessStatusCode)
            {
                var errorMessage = "Unknown error";
                if (result.TryGetProperty("error", out var errorObj) &&
                    errorObj.TryGetProperty("message", out var msgProp))
                {
                    errorMessage = msgProp.GetString() ?? errorMessage;
                }
                return BadRequest(new { error = new { sessionResult = (string?)null, message = errorMessage } });
            }

            return Ok(result);
        }
        catch (Exception ex)
        {
            return BadRequest(new { error = new { sessionResult = (string?)null, message = ex.Message } });
        }
    }
}
