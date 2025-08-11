//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

using AzureAIVisionFaceUIHandler;

namespace FaceAnalyzerSample;

internal static partial class FaceAPI {
    internal static FaceAPIToken? GetToken()
    {
        using var client = new HttpClient();
        client.DefaultRequestHeaders.Add("Ocp-Apim-Subscription-Key", FaceAPI.SubscriptionKey);

        var payload = new Dictionary<string, object>
        {
            ["livenessOperationMode"] = "PassiveActive",
            ["deviceCorrelationId"] = Guid.NewGuid().ToString(),
            ["userCorrelationId"] = Guid.NewGuid().ToString()
        };

        var jsonPayload = System.Text.Json.JsonSerializer.Serialize(payload);
        using var content = new StringContent(jsonPayload, System.Text.Encoding.UTF8, "application/json");
        var postResponse = client.PostAsync($"https://{FaceAPI.Endpoint}/face/v1.2/detectLiveness-sessions", content).Result;
        if (!postResponse.IsSuccessStatusCode)
        {
            return null;
        }
        var jsonString = postResponse.Content.ReadAsStringAsync().Result;
        using var doc = System.Text.Json.JsonDocument.Parse(jsonString);
        if (doc.RootElement.TryGetProperty("sessionId", out var sessionIdElement) &&
            sessionIdElement.GetString() is string sessionId &&
            doc.RootElement.TryGetProperty("authToken", out var authTokenElement) &&
            authTokenElement.GetString() is string authToken)
        {
            return new FaceAPIToken
            {
                SessionId = sessionId,
                AuthToken = authToken
            };
        }
        return null;
    }

    internal static string? GetResult(string sessionId)
    {
        using var client = new HttpClient();
        client.DefaultRequestHeaders.Add("Ocp-Apim-Subscription-Key", FaceAPI.SubscriptionKey);

        var getResponse = client.GetAsync($"https://{FaceAPI.Endpoint}/face/v1.2/detectLiveness-sessions/{sessionId}").Result;
        if (!getResponse.IsSuccessStatusCode)
        {
            return null;
        }
        var jsonString = getResponse.Content.ReadAsStringAsync().Result;
        using var doc = System.Text.Json.JsonDocument.Parse(jsonString);
        if (doc.RootElement.TryGetProperty("results", out var resultsElement) &&
            resultsElement.TryGetProperty("attempts", out var attemptsElement) &&
            attemptsElement.ValueKind == System.Text.Json.JsonValueKind.Array &&
            attemptsElement.GetArrayLength() > 0 &&
            attemptsElement[0].TryGetProperty("result", out var resultElement) &&
            resultElement.TryGetProperty("livenessDecision", out var livenessDecisionElement) &&
            livenessDecisionElement.GetString() is string livenessDecision)
        {
            return livenessDecision;
        }
        return null;
    }
}
