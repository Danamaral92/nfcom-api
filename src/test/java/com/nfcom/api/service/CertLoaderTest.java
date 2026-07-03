package com.nfcom.api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class CertLoaderTest {

    private static final String TEST_CERT_PATH = "src/test/resources/test-cert.p12";
    private static final String TEST_CERT_PASSWORD = "changeit";

    @Test
    void loadsKeyStoreFromP12File() {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        KeyStore ks = loader.getKeyStore();
        assertNotNull(ks, "KeyStore should be loaded");
    }

    @Test
    void extractsPrivateKey() {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        PrivateKey privateKey = loader.getPrivateKey();
        assertNotNull(privateKey, "PrivateKey should be extracted");
    }

    @Test
    void extractsX509Certificate() {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        X509Certificate cert = loader.getCertificate();
        assertNotNull(cert, "X509Certificate should be extracted");
    }

    @Test
    void certificateHasExpectedSubject() throws Exception {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        X509Certificate cert = loader.getCertificate();
        String subject = cert.getSubjectX500Principal().getName();
        assertTrue(subject.contains("NFCom Test"), "Subject should contain 'NFCom Test', got: " + subject);
    }

    @Test
    void createsSslContext() {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        SSLContext sslContext = loader.createSslContext();
        assertNotNull(sslContext, "SSLContext should be created");
        assertEquals("TLSv1.2", sslContext.getProtocol());
    }

    @Test
    void sslContextUsesTls12() throws Exception {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        SSLContext sslContext = loader.createSslContext();
        assertEquals("TLSv1.2", sslContext.getProtocol());
    }

    @Test
    void loaderCanHandleNullCertPathGracefully() {
        CertLoader loader = createLoader("", "");
        assertDoesNotThrow(loader::init);
        assertNull(loader.getKeyStore());
        assertNull(loader.getPrivateKey());
        assertNull(loader.getCertificate());
        assertNull(loader.createSslContext());
    }

    @Test
    void certificateIsNotExpired() {
        CertLoader loader = createLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD);
        loader.init();
        X509Certificate cert = loader.getCertificate();
        assertNotNull(cert);
        // Certificate should be valid (not expired) - our test cert is valid for 365 days
        assertTrue(cert.getNotAfter().getTime() > System.currentTimeMillis(),
                "Certificate should not be expired");
    }

    /**
     * Helper to create a CertLoader with given path and password,
     * simulating what CDI would inject via @ConfigProperty.
     */
    private CertLoader createLoader(String certPath, String certPassword) {
        return new CertLoader(certPath, certPassword, null);
    }
}
