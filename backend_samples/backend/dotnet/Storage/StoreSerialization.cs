using System.Text.Json;
using System.Text.Json.Serialization;

namespace FaceLivenessAttestationBackendSample.Storage;

/// <summary>Serialization options for values persisted by the stores.</summary>
internal static class StoreSerialization
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };
}
