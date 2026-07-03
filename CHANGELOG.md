# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-07-03

### Added

- **NFCom Submission** — `POST /api/v1/nfcom/submit` endpoint that converts JSON to NFCom XML, applies XML Digital Signature (RSA-SHA1, C14N, enveloped), compresses with GZip+Base64, and sends to SEFAZ via SOAP 1.2 over mTLS
- **NFCom Query** — `GET /api/v1/nfcom/{accessKey}` endpoint to query NFCom status by 44-digit access key
- **SEFAZ Status** — `GET /api/v1/nfcom/status` endpoint to check SEFAZ NFCom service status (cStat 107/108/109)
- **NFCom Events** — `POST /api/v1/nfcom/events` endpoint supporting all 8 event types (110111, 240140, 240150, 240151, 240160, 240161, 240162, 240170)
- **Taxpayer Query** — `GET /api/v1/nfcom/taxpayer/{cnpj}` endpoint for taxpayer registration lookup by 14-digit CNPJ
- **XML Digital Signature** — Enveloped signature with RSA-SHA1, canonicalization C14N, X509Certificate-only KeyInfo, and #NFCom{accessKey} reference URI
- **mTLS with ICP-Brasil A1 Certificates** — SSLContext loaded from `.pfx`/`.p12` files at startup with custom truststore support
- **Rate Limiting Backoff** — Exponential backoff with jitter for SEFAZ cStat 678 (Consumo Indevido) responses, configurable retries and base delay
- **Input Validation** — Strict validation for 44-digit access keys, 14-digit CNPJs, and whitelisted event types (8 values)
- **Standardized Error Envelope** — Consistent JSON error format with `{status, error: {code, message, details}}` across all endpoints
- **OpenAPI Documentation** — Auto-generated Swagger UI at `/q/swagger-ui/` and OpenAPI spec at `/q/openapi`
- **Docker Deployment** — Multi-stage Dockerfile and docker-compose.yml with healthcheck, resource limits, and cert volume mount
- **Comprehensive Test Suite** — 164 unit tests covering all layers (resource, service, client, XML, validation, error models, Docker delivery) with WireMock SOAP stubs

[1.0.0]: https://github.com/daniel/nfcom-api/releases/tag/v1.0.0
