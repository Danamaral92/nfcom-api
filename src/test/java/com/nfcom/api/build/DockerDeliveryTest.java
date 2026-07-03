package com.nfcom.api.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification tests for Phase 7 — Docker Delivery (T14).
 * <p>
 * Checks that all required Docker delivery artifacts exist,
 * have proper structure, and document the necessary environment variables.
 */
class DockerDeliveryTest {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();

    // ---------------------------------------------------------------
    // Dockerfile
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Dockerfile")
    class DockerfileTest {

        @Test
        @DisplayName("must exist at project root")
        void dockerfileExists() {
            assertTrue(Files.exists(PROJECT_ROOT.resolve("Dockerfile")),
                    "Dockerfile must exist at project root");
        }

        @Test
        @DisplayName("must use multi-stage build")
        void dockerfileHasMultiStageBuild() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
            assertTrue(content.contains("FROM maven:3.9-eclipse-temurin-21-alpine AS build"),
                    "Dockerfile must have a build stage with Maven + Temurin 21");
            assertTrue(content.contains("FROM eclipse-temurin:21-jre-alpine"),
                    "Dockerfile must have a run stage with Temurin 21 JRE");
        }

        @Test
        @DisplayName("must expose port 8080")
        void dockerfileExposesPort() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
            assertTrue(content.contains("EXPOSE 8080"),
                    "Dockerfile must expose port 8080");
        }

        @Test
        @DisplayName("must create and use non-root user")
        void dockerfileHasNonRootUser() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
            assertTrue(content.contains("addgroup -S nfcom"),
                    "Dockerfile must create nfcom group");
            assertTrue(content.contains("adduser -S nfcom -G nfcom"),
                    "Dockerfile must create nfcom user");
            assertTrue(content.contains("USER nfcom"),
                    "Dockerfile must switch to nfcom user");
        }

        @Test
        @DisplayName("must define NFCOM environment variables")
        void dockerfileHasEnvVars() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
            assertTrue(content.contains("NFCOM_CERT_PATH"),
                    "Dockerfile must define NFCOM_CERT_PATH");
            assertTrue(content.contains("NFCOM_CERT_PASSWORD"),
                    "Dockerfile must define NFCOM_CERT_PASSWORD");
            assertTrue(content.contains("NFCOM_SEFAZ_URL"),
                    "Dockerfile must define NFCOM_SEFAZ_URL");
        }

        @Test
        @DisplayName("must copy runner jar and lib directory")
        void dockerfileCopiesArtifacts() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
            assertTrue(content.contains("*-runner.jar"),
                    "Dockerfile must copy the runner JAR");
            assertTrue(content.contains("target/lib/"),
                    "Dockerfile must copy the lib directory");
        }
    }

    // ---------------------------------------------------------------
    // docker-compose.yml
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("docker-compose.yml")
    class DockerComposeTest {

        @Test
        @DisplayName("must exist at project root")
        void dockerComposeExists() {
            assertTrue(Files.exists(PROJECT_ROOT.resolve("docker-compose.yml")),
                    "docker-compose.yml must exist at project root");
        }

        @Test
        @DisplayName("must define nfcom-api service")
        void dockerComposeHasNfcomApiService() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
            assertTrue(content.contains("nfcom-api:"),
                    "docker-compose.yml must define nfcom-api service");
        }

        @Test
        @DisplayName("must expose port 8080")
        void dockerComposeExposesPort() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
            assertTrue(content.contains("\"8080:8080\""),
                    "docker-compose.yml must map port 8080");
        }

        @Test
        @DisplayName("must pass environment variables from .env")
        void dockerComposeHasEnvironmentVars() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
            assertTrue(content.contains("NFCOM_CERT_PATH"),
                    "docker-compose.yml must pass NFCOM_CERT_PATH");
            assertTrue(content.contains("NFCOM_CERT_PASSWORD"),
                    "docker-compose.yml must pass NFCOM_CERT_PASSWORD");
            assertTrue(content.contains("NFCOM_SEFAZ_URL"),
                    "docker-compose.yml must pass NFCOM_SEFAZ_URL");
        }

        @Test
        @DisplayName("must mount certificate as read-only volume")
        void dockerComposeHasCertVolume() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
            assertTrue(content.contains("./cert.p12:/app/cert.p12:ro"),
                    "docker-compose.yml must mount cert.p12 as read-only");
        }

        @Test
        @DisplayName("must have restart policy")
        void dockerComposeHasRestartPolicy() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
            assertTrue(content.contains("restart: unless-stopped"),
                    "docker-compose.yml must have restart policy");
        }
    }

    // ---------------------------------------------------------------
    // .env.example
    // ---------------------------------------------------------------

    @Nested
    @DisplayName(".env.example")
    class EnvExampleTest {

        @Test
        @DisplayName("must exist at project root")
        void envExampleExists() {
            assertTrue(Files.exists(PROJECT_ROOT.resolve(".env.example")),
                    ".env.example must exist at project root");
        }

        @Test
        @DisplayName("must document NFCOM_CERT_PASSWORD")
        void envExampleHasCertPassword() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve(".env.example"));
            assertTrue(content.contains("NFCOM_CERT_PASSWORD"),
                    ".env.example must document NFCOM_CERT_PASSWORD");
        }

        @Test
        @DisplayName("must document NFCOM_CERT_PATH")
        void envExampleHasCertPath() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve(".env.example"));
            assertTrue(content.contains("NFCOM_CERT_PATH"),
                    ".env.example must document NFCOM_CERT_PATH");
        }

        @Test
        @DisplayName("must document NFCOM_SEFAZ_URL")
        void envExampleHasSefazUrl() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve(".env.example"));
            assertTrue(content.contains("NFCOM_SEFAZ_URL"),
                    ".env.example must document NFCOM_SEFAZ_URL");
        }

        @Test
        @DisplayName("must document optional NFCOM_TRUSTSTORE_PATH")
        void envExampleHasTruststore() throws IOException {
            String content = Files.readString(PROJECT_ROOT.resolve(".env.example"));
            assertTrue(content.contains("NFCOM_TRUSTSTORE_PATH"),
                    ".env.example must document NFCOM_TRUSTSTORE_PATH");
        }
    }

    // ---------------------------------------------------------------
    // application.properties — container configuration
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("application.properties container config")
    class ApplicationPropertiesTest {

        @Test
        @DisplayName("must bind to 0.0.0.0")
        void bindsToAllInterfaces() throws IOException {
            String content = readApplicationProperties();
            assertTrue(content.contains("quarkus.http.host=0.0.0.0"),
                    "application.properties must set quarkus.http.host=0.0.0.0");
        }

        @Test
        @DisplayName("must use port 8080")
        void usesPort8080() throws IOException {
            String content = readApplicationProperties();
            assertTrue(content.contains("quarkus.http.port=8080"),
                    "application.properties must set quarkus.http.port=8080");
        }

        @Test
        @DisplayName("must configure Swagger UI path")
        void hasSwaggerUiPath() throws IOException {
            String content = readApplicationProperties();
            assertTrue(content.contains("quarkus.swagger-ui.path=/q/swagger-ui"),
                    "application.properties must set quarkus.swagger-ui.path");
        }

        @Test
        @DisplayName("must configure OpenAPI path")
        void hasOpenApiPath() throws IOException {
            String content = readApplicationProperties();
            assertTrue(content.contains("quarkus.smallrye-openapi.path=/q/openapi"),
                    "application.properties must set quarkus.smallrye-openapi.path");
        }

        @Test
        @DisplayName("must have swagger-ui.always-include=true")
        void swaggerUiAlwaysInclude() throws IOException {
            String content = readApplicationProperties();
            assertTrue(content.contains("quarkus.swagger-ui.always-include=true"),
                    "application.properties must always include Swagger UI");
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static String readApplicationProperties() throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve("src/main/resources/application.properties"));
    }
}
