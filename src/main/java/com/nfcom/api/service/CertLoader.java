package com.nfcom.api.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Loads an ICP-Brasil A1 certificate from a PKCS12 (.pfx/.p12) file and
 * provides access to the private key, X509 certificate, and a pre-configured
 * SSLContext for mTLS communication with SEFAZ.
 * <p>
 * Reads configuration from {@code nfcom.cert.path} and {@code nfcom.cert.password}
 * (mapped from environment variables {@code NFCOM_CERT_PATH} and
 * {@code NFCOM_CERT_PASSWORD}).
 * <p>
 * If the certificate path is empty or not configured, the loader skips
 * initialization gracefully — allowing the application to run in dev/test
 * environments without a certificate.
 */
@ApplicationScoped
public class CertLoader {

    private static final Logger log = Logger.getLogger(CertLoader.class);

    private final String certPath;
    private final String certPassword;
    private final String truststorePath;

    private KeyStore keyStore;
    private PrivateKey privateKey;
    private X509Certificate certificate;
    private SSLContext sslContext;

    /**
     * Default constructor used by CDI. Config values are injected via
     {@code @ConfigProperty}
     * from {@code application.properties}, which reads from environment variables.
     */
    public CertLoader() {
        this.certPath = readConfig("nfcom.cert.path", "");
        this.certPassword = readConfig("nfcom.cert.password", "");
        this.truststorePath = readConfig("nfcom.truststore.path", "");
    }

    /**
     * Constructor for testing — allows direct injection of path and password.
     */
    CertLoader(String certPath, String certPassword, String truststorePath) {
        this.certPath = certPath;
        this.certPassword = certPassword;
        this.truststorePath = truststorePath;
    }

    /**
     * Initializes the certificate loader.
     * Called automatically by CDI after construction.
     * If {@code certPath} is empty, initialization is skipped.
     */
    @PostConstruct
    public void init() {
        if (certPath == null || certPath.isBlank()) {
            log.warn("NFCOM_CERT_PATH not configured — skipping certificate loading. "
                    + "mTLS to SEFAZ will not be available.");
            return;
        }
        try {
            loadKeyStore();
            extractPrivateKeyAndCertificate();
            buildSslContext();
            log.infov("Certificate loaded successfully from: {0}", certPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load certificate from: " + certPath, e);
        }
    }

    /**
     * Creates and returns the pre-configured SSLContext for mTLS.
     *
     * @return the SSLContext, or {@code null} if no certificate is configured
     */
    public SSLContext createSslContext() {
        return sslContext;
    }

    /**
     * Returns the loaded KeyStore.
     *
     * @return the KeyStore, or {@code null} if not initialized
     */
    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * Returns the private key extracted from the certificate.
     *
     * @return the PrivateKey, or {@code null} if not initialized
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the X509 certificate extracted from the keystore.
     *
     * @return the X509Certificate, or {@code null} if not initialized
     */
    public X509Certificate getCertificate() {
        return certificate;
    }

    // -----------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------

    private void loadKeyStore() throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException {
        try (InputStream is = new FileInputStream(certPath)) {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(is, certPassword.toCharArray());
        }
    }

    private void extractPrivateKeyAndCertificate() throws GeneralSecurityException {
        String alias = keyStore.aliases().nextElement();
        privateKey = (PrivateKey) keyStore.getKey(alias, certPassword.toCharArray());
        certificate = (X509Certificate) keyStore.getCertificate(alias);
    }

    private void buildSslContext() throws GeneralSecurityException, IOException {
        // Initialize KeyManagerFactory with the client certificate keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, certPassword.toCharArray());

        // Initialize TrustManager: custom truststore or JVM default
        TrustManager[] trustManagers;
        if (truststorePath != null && !truststorePath.isBlank()) {
            trustManagers = createTrustManagers(truststorePath);
        } else {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // JVM default truststore
            trustManagers = tmf.getTrustManagers();
        }

        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
    }

    private static TrustManager[] createTrustManagers(String truststorePath)
            throws GeneralSecurityException, IOException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream is = new FileInputStream(truststorePath)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(is, null);
            tmf.init(trustStore);
        }
        return tmf.getTrustManagers();
    }

    /**
     * Reads a configuration value by attempting system property first,
     * then falling back to environment variable.
     * <p>
     * This simulates what {@code @ConfigProperty} would do in a full Quarkus
     * environment, allowing the class to work in plain JUnit tests.
     */
    private static String readConfig(String propertyName, String defaultValue) {
        // Try system property (set by Quarkus test or command line)
        String value = System.getProperty(propertyName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        // Try environment variable (mapped from property naming convention)
        String envName = propertyName.toUpperCase().replace('.', '_');
        value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }
}
