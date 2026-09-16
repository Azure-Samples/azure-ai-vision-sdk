package com.azure.ai.vision.face.deviceattestation.crypto;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

/** PEM encode/decode helpers shared by the key and certificate utilities. */
public final class PemUtils {

    /** Wrap DER bytes in a PEM block of the given type (64-char lines). */
    public static String toPem(String type, byte[] der) {
        StringWriter output = new StringWriter();
        try (PemWriter writer = new PemWriter(output)) {
            writer.writeObject(new PemObject(type, der));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to encode PEM", exception);
        }
        return output.toString().replace("\r\n", "\n");
    }

    /** Strip PEM armor + whitespace and base64-decode the body to DER bytes. */
    public static byte[] pemBodyToDer(String pem) {
        return pemBodyToDer(pem, null);
    }

    public static byte[] pemBodyToDer(String pem, String expectedType) {
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject object = reader.readPemObject();
            if (object == null || (expectedType != null && !expectedType.equals(object.getType()))
                    || reader.readPemObject() != null) {
                throw new IllegalArgumentException("Expected a single PEM block with the requested label");
            }
            return object.getContent();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid PEM", exception);
        }
    }

    private PemUtils() {
    }
}
