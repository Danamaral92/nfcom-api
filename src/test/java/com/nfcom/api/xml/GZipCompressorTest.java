package com.nfcom.api.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GZipCompressorTest {

    @Test
    void compressReturnsNonEmptyByteArray() {
        byte[] compressed = GZipCompressor.compress("<test>dados</test>");
        assertNotNull(compressed);
        assertTrue(compressed.length > 0, "Compressed output should not be empty");
    }

    @Test
    void compressToBase64ReturnsBase64String() {
        String result = GZipCompressor.compressToBase64("<test>dados</test>");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Base64 pattern: alphanumeric + / + = padding
        assertTrue(result.matches("^[A-Za-z0-9+/=]+$"),
                "Output should be valid Base64, got: " + result);
    }

    @Test
    void decompressFromBase64ReturnsOriginalString() {
        String original = "<nfcom><ide><cUF>35</cUF></ide></nfcom>";
        String compressed = GZipCompressor.compressToBase64(original);
        String decompressed = GZipCompressor.decompressFromBase64(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    void roundTripWithLargeXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<root>");
        for (int i = 0; i < 100; i++) {
            sb.append("<item id=\"").append(i).append("\">value").append(i).append("</item>");
        }
        sb.append("</root>");
        String large = sb.toString();

        String compressed = GZipCompressor.compressToBase64(large);
        String decompressed = GZipCompressor.decompressFromBase64(compressed);
        assertEquals(large, decompressed);
    }

    @Test
    void compressAndCompressToBase64AreConsistent() {
        String xml = "<Nfcom><infNFe Id=\"NFe35200600012345000176550000000012345678901234\"></infNFe></Nfcom>";
        byte[] rawCompressed = GZipCompressor.compress(xml);
        String base64Compressed = GZipCompressor.compressToBase64(xml);

        byte[] expectedBase64Bytes = java.util.Base64.getEncoder().encode(rawCompressed);
        String expectedBase64 = new String(expectedBase64Bytes, StandardCharsets.UTF_8);
        assertEquals(expectedBase64, base64Compressed);
    }

    @Test
    void decompressFromBase64HandlesEmptyString() {
        String original = "";
        String compressed = GZipCompressor.compressToBase64(original);
        String decompressed = GZipCompressor.decompressFromBase64(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    void compressHandlesSpecialCharacters() {
        String xml = "<motivo>Cancelamento por erro na emissão — NFCom 123</motivo>";
        String compressed = GZipCompressor.compressToBase64(xml);
        String decompressed = GZipCompressor.decompressFromBase64(compressed);
        assertEquals(xml, decompressed);
    }

    @Test
    void threadSafety() throws InterruptedException {
        String xml = "<test>thread safety test</test>";
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    String compressed = GZipCompressor.compressToBase64(xml);
                    String decompressed = GZipCompressor.decompressFromBase64(compressed);
                    assertEquals(xml, decompressed);
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(10000);
    }

    @Test
    void compressBytesProducesOutput() {
        byte[] input = "Hello NFCom World".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = GZipCompressor.compress(input);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0, "Compressed output should not be empty");
    }
}
