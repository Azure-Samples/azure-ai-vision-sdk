using System.Formats.Cbor;

namespace Azure.AI.Vision.Face.DeviceAttestation.Ios;

/// <summary>
/// CBOR adapter for Apple App Attest payloads. Supports unsigned
/// int, negative int, byte string, text string, array, and map. Indefinite
/// lengths, tags, and floats are rejected so malformed input fails loudly.
/// Values are boxed as: long, string, byte[], List&lt;object?&gt;, or
/// Dictionary&lt;object, object?&gt; (string/long keys).
/// </summary>
internal static class Cbor
{
    public static (object? Value, int NextPos) Decode(byte[] buf, int pos = 0)
    {
        var reader = new CborReader(buf.AsMemory(pos), CborConformanceMode.Lax, allowMultipleRootLevelValues: true);
        var value = ReadValue(reader, 0);
        return (value, buf.Length - reader.BytesRemaining);
    }

    private static object ReadValue(CborReader reader, int depth)
    {
        if (depth > 16) throw new FormatException("CBOR: max nesting depth exceeded");
        switch (reader.PeekState())
        {
            case CborReaderState.UnsignedInteger:
            case CborReaderState.NegativeInteger:
                return reader.ReadInt64();
            case CborReaderState.ByteString:
                return reader.ReadByteString();
            case CborReaderState.TextString:
                return reader.ReadTextString();
            case CborReaderState.StartArray:
            {
                int length = reader.ReadStartArray() ?? throw new FormatException("CBOR: indefinite array");
                var arr = new List<object?>();
                for (int index = 0; index < length; index++)
                    arr.Add(ReadValue(reader, depth + 1));
                reader.ReadEndArray();
                return arr;
            }
            case CborReaderState.StartMap:
            {
                int length = reader.ReadStartMap() ?? throw new FormatException("CBOR: indefinite map");
                var map = new Dictionary<object, object?>();
                for (int index = 0; index < length; index++)
                {
                    var key = ReadValue(reader, depth + 1);
                    if (key is not string && key is not long)
                        throw new FormatException("CBOR: unsupported map key type");
                    map[key] = ReadValue(reader, depth + 1);
                }
                reader.ReadEndMap();
                return map;
            }
            default:
                throw new FormatException($"CBOR: unsupported state {reader.PeekState()}");
        }
    }
}
