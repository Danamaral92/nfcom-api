package com.nfcom.api.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Thread-safe utility for GZip compression and Base64 encoding of XML payloads.
 * <p>
 * Used primarily for NFComRecepcao service which requires the signed XML to be
 * GZip-compressed then Base64-encoded inside the SOAP envelope.
 */
public final class GZipCompressor {

    private GZipCompressor() {
        // Utility class — no instantiation
    }

    /**
     * Compresses raw bytes using GZip.
     *
     * @param data the data to compress
     * @return GZip-compressed bytes
     */
    public static byte[] compress(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to GZip compress data", e);
        }
    }

    /**
     * Compresses an XML string (UTF-8) using GZip.
     *
     * @param xml the XML string to compress
     * @return GZip-compressed bytes
     */
    public static byte[] compress(String xml) {
        return compress(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * GZip-compresses an XML string, then Base64-encodes the result.
     *
     * @param xml the XML string to compress and encode
     * @return Base64-encoded GZip-compressed string
     */
    public static String compressToBase64(String xml) {
        byte[] compressed = compress(xml);
        return Base64.getEncoder().encodeToString(compressed);
    }

    /**
     * Base64-decodes then GZip-decompresses a string back to the original XML.
     *
     * @param base64 the Base64-encoded GZip-compressed string
     * @return the original decompressed string (UTF-8)
     */
    public static String decompressFromBase64(String base64) {
        byte[] compressed = Base64.getDecoder().decode(base64);
        return decompress(compressed);
    }

    /**
     * Decompresses GZip-compressed bytes back to a UTF-8 string.
     *
     * @param compressed the GZip-compressed bytes
     * @return the decompressed string
     */
    public static String decompress(byte[] compressed) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to GZip decompress data", e);
        }
    }
}
