namespace Azure.AI.Vision.Face.DeviceAttestation;

/// <summary>ISO-8601 UTC timestamp helpers (round-trip "o" format, always "Z").</summary>
internal static class IsoTime
{
    public static string Now() => DateTime.UtcNow.ToString("o");

    public static string From(DateTimeOffset value) => value.UtcDateTime.ToString("o");
}
