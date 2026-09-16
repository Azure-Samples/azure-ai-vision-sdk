using Microsoft.ApplicationInsights;
using System.Text.Json;
using Azure.AI.Vision.Face.DeviceAttestation.Logging;
using AiDependency = Microsoft.ApplicationInsights.DataContracts.DependencyTelemetry;
using AiEvent = Microsoft.ApplicationInsights.DataContracts.EventTelemetry;
using AiException = Microsoft.ApplicationInsights.DataContracts.ExceptionTelemetry;

namespace FaceLivenessAttestationBackendSample.Services;

/// <summary>
/// Application Insights implementation of the library's <see cref="IAttestationLogger"/>.
/// If App Insights is not configured, telemetry is written through the host's
/// logging pipeline, which uses the console provider by default.
/// </summary>
internal sealed class AppInsightsAttestationLogger : IAttestationLogger
{
    private readonly TelemetryClient? _client;
    private readonly ILogger<AppInsightsAttestationLogger> _logger;

    public AppInsightsAttestationLogger(
        TelemetryClient? client,
        ILogger<AppInsightsAttestationLogger> logger)
    {
        _client = client;
        _logger = logger;
    }

    public void TrackEvent(string name, IReadOnlyDictionary<string, object?>? properties = null, IReadOnlyDictionary<string, double>? measurements = null)
    {
        if (_client is null)
        {
            _logger.LogInformation(
                "[Attestation] Event {EventName} Properties {Properties} Measurements {Measurements}",
                name,
                Serialize(properties),
                Serialize(measurements));
            return;
        }
        var evt = new AiEvent(name);
        AddProperties(evt.Properties, properties);
        _client.TrackEvent(evt);

        // Application Insights 3.x no longer supports measurements on
        // EventTelemetry. Emit them as standalone metrics instead.
        if (measurements is not null)
        {
            foreach (var m in measurements)
            {
                _client.TrackMetric(m.Key, m.Value);
            }
        }
    }

    public void TrackException(Exception error, IReadOnlyDictionary<string, object?>? properties = null)
    {
        if (_client is null)
        {
            _logger.LogError(
                error,
                "[Attestation] Exception {ExceptionType}: {ExceptionMessage} Properties {Properties}",
                error.GetType().Name,
                error.Message,
                Serialize(properties));
            return;
        }
        var telemetry = new AiException(error);
        AddProperties(telemetry.Properties, properties);
        _client.TrackException(telemetry);
    }

    public void TrackDependency(DependencyTelemetry dependency)
    {
        if (_client is null)
        {
            _logger.LogInformation(
                "[Attestation] Dependency {DependencyName} Target {Target} Data {Data} Success {Success} ResultCode {ResultCode} DurationMs {DurationMs} Type {DependencyType} Properties {Properties}",
                dependency.Name,
                dependency.Target,
                dependency.Data,
                dependency.Success,
                dependency.ResultCode,
                dependency.Duration.TotalMilliseconds,
                dependency.DependencyTypeName,
                Serialize(dependency.Properties));
            return;
        }
        var telemetry = new AiDependency
        {
            Name = dependency.Name,
            Target = dependency.Target,
            Data = dependency.Data,
            Duration = dependency.Duration,
            Success = dependency.Success,
            ResultCode = dependency.ResultCode,
            Type = dependency.DependencyTypeName,
        };
        AddProperties(telemetry.Properties, dependency.Properties);
        _client.TrackDependency(telemetry);
    }

    private static void AddProperties(IDictionary<string, string> target, IReadOnlyDictionary<string, object?>? properties)
    {
        if (properties is null)
        {
            return;
        }
        foreach (var p in properties)
        {
            target[p.Key] = p.Value?.ToString() ?? "";
        }
    }

    private static string Serialize(object? value)
    {
        if (value is null)
        {
            return "{}";
        }
        try
        {
            return JsonSerializer.Serialize(value);
        }
        catch
        {
            return value.ToString() ?? "{}";
        }
    }
}
