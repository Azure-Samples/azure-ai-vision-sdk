namespace FaceLivenessDetectorSample.Shared.Models;

public class GenerateTokenRequest
{
    public string Action { get; set; } = string.Empty;
    public string LivenessOperationMode { get; set; } = string.Empty;
    public string DeviceCorrelationId { get; set; } = string.Empty;
    public string UserCorrelationId { get; set; } = string.Empty;
}
