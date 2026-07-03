# NFCom API

REST wrapper for SEFAZ NFCom (Model 62) web services — Brazilian electronic invoice for communication services.

Built with Quarkus 3.x LTS and Java 21.

## Quick Start

```bash
# 1. Clone and configure
cp .env.example .env
# Edit .env — set NFCOM_CERT_PASSWORD to your certificate password

# 2. Start with Docker Compose
docker compose up

# 3. API is available at http://localhost:8080
# Swagger UI: http://localhost:8080/q/swagger-ui/
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `NFCOM_CERT_PATH` | Yes | `/app/cert.p12` | Path to PFX/P12 certificate for mTLS with SEFAZ |
| `NFCOM_CERT_PASSWORD` | Yes | — | Certificate password |
| `NFCOM_SEFAZ_URL` | No | `https://dfe-portal.svrs.rs.gov.br/NFCom` | SEFAZ endpoint URL |
| `NFCOM_TRUSTSTORE_PATH` | No | — | Custom truststore path |

## API Documentation

Swagger UI is available at `/q/swagger-ui/` when the application is running.

The OpenAPI specification is available at `/q/openapi`.

## Development

Build and run locally without Docker:

```bash
# Build
mvn clean package -DskipTests

# Run in dev mode
mvn quarkus:dev

# Run tests
mvn test
```
