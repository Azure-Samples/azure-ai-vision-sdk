using System.Text.Json;
using System.Text.Json.Serialization;

namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>Shared serialization options for values the library persists (cert metadata).</summary>
internal static class LibraryJson
{
    public static readonly JsonSerializerOptions Metadata = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };
}
