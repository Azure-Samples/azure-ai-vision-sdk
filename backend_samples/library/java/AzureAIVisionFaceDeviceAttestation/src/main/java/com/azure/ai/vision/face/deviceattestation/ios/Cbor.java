package com.azure.ai.vision.face.deviceattestation.ios;

import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.MAJOR_TYPE_INT_POS;
import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.MAJOR_TYPE_INT_NEG;
import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.MAJOR_TYPE_TEXT;
import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.MAJOR_TYPE_TAG;
import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.SUFFIX_INDEFINITE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

/**
 * CBOR adapter for Apple App Attest payloads. Supports unsigned
 * int, negative int, byte string, text string, array, and map. Indefinite
 * lengths, tags, and floats are rejected so malformed input fails loudly.
 * Values are boxed as: Long, String, byte[], List&lt;Object&gt;, or
 * Map&lt;Object, Object&gt; (String/Long keys).
 */
public final class Cbor {
    private static final int MAX_NESTING_DEPTH = 16;
    private static final int MAJOR_TYPE_SHIFT = 5;
    private static final int ADDITIONAL_INFO_MASK = 0x1f;

    private static final CBORFactory FACTORY = CBORFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH + 1).build()).build();

    /** A decoded CBOR value plus the offset immediately after it. */
    public record Decoded(Object value, int nextPos) {
    }

    public static Decoded decode(byte[] buf) {
        return decode(buf, 0);
    }

    public static Decoded decode(byte[] buf, int pos) {
        if (pos < 0 || pos >= buf.length) throw new IllegalArgumentException("CBOR: invalid offset");
        try (CBORParser parser = FACTORY.createParser(buf, pos, buf.length - pos)) {
            parser.nextToken();
            Object value = readValue(parser, buf, 0);
            return new Decoded(value, Math.toIntExact(parser.currentLocation().getByteOffset()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("CBOR: malformed input", exception);
        }
    }

    private static Object readValue(CBORParser parser, byte[] buf, int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) throw new IllegalArgumentException("CBOR: max nesting depth exceeded");
        checkHeader(parser, buf);
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
                return parser.getLongValue();
            case VALUE_STRING:
                return parser.getText();
            case VALUE_EMBEDDED_OBJECT:
                return parser.getBinaryValue();
            case START_ARRAY: {
                List<Object> array = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY)
                    array.add(readValue(parser, buf, depth + 1));
                return array;
            }
            case START_OBJECT: {
                Map<Object, Object> map = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    int majorType = checkHeader(parser, buf) >> MAJOR_TYPE_SHIFT;
                    if (majorType != MAJOR_TYPE_INT_POS && majorType != MAJOR_TYPE_INT_NEG && majorType != MAJOR_TYPE_TEXT)
                        throw new IllegalArgumentException("CBOR: unsupported map key type");
                    Object key = majorType == MAJOR_TYPE_TEXT ? parser.currentName() : Long.valueOf(parser.currentName());
                    parser.nextToken();
                    map.put(key, readValue(parser, buf, depth + 1));
                }
                return map;
            }
            default:
                throw new IllegalArgumentException("CBOR: unsupported token " + parser.currentToken());
        }
    }

    private static int checkHeader(CBORParser parser, byte[] buf) {
        if (parser.currentToken() == null) throw new IllegalArgumentException("CBOR: unexpected end of input");
        int header = Byte.toUnsignedInt(buf[Math.toIntExact(parser.currentTokenLocation().getByteOffset())]);
        if ((header & ADDITIONAL_INFO_MASK) == SUFFIX_INDEFINITE || (header >> MAJOR_TYPE_SHIFT) == MAJOR_TYPE_TAG)
            throw new IllegalArgumentException("CBOR: indefinite encodings and tags are unsupported");
        return header;
    }

    private Cbor() {
    }
}
