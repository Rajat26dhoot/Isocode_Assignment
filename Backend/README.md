# Customs Operations Backend

Spring Boot 3 / Java 17 API for shipment documents, extraction, cross-document validation, executive review, manager approval, audit, and generic CSV/XLSX exports.

## Configuration

Configure these environment variables through the deployment secret manager:

```text
AIVEN_DB_HOST
AIVEN_DB_PORT
AIVEN_DB_NAME
AIVEN_DB_USER
AIVEN_DB_PASSWORD
JWT_SECRET                         # at least 32 bytes
AES_ENCRYPTION_KEY                 # stable secret used for encrypted shipment data
APP_CORS_ALLOWED_ORIGINS           # comma-separated exact frontend origins
EXTRACTION_PROVIDER                # fallback, openai, or gemini
OPENAI_API_KEY                     # required when provider=openai
OPENAI_VISION_MODEL                # optional, defaults to gpt-4o-mini
GEMINI_API_KEY                     # required when provider=gemini; Google AI Studio free-tier key
GEMINI_VISION_MODEL                # optional, defaults to gemini-3.5-flash-lite (fast document extraction)
GEMINI_READ_TIMEOUT_MS             # optional, defaults to 45000
BOOTSTRAP_EXECUTIVE_EMAIL          # optional first account
BOOTSTRAP_EXECUTIVE_PASSWORD       # at least 12 characters
BOOTSTRAP_MANAGER_EMAIL           # optional first account
BOOTSTRAP_MANAGER_PASSWORD        # at least 12 characters
```

The bootstrap account settings only create a user when that email does not already exist. No demo users or sample shipments are inserted. Configure deployment TLS, database region, provider data controls, backups, and key rotation separately; this repository does not verify hosting location or external-provider data-training settings. The Gemini adapter uses the stateless Interactions API for inline image/PDF extraction (`store=false`); use `EXTRACTION_PROVIDER=gemini` and provide `GEMINI_API_KEY`. Its request format follows the [AI Studio image understanding guide](https://aistudio.google.com/docs/image-understanding) and [Interactions API guide](https://aistudio.google.com/docs/interactions-overview).

Run with `mvn spring-boot:run` or build with `mvn package`. JPA uses `ddl-auto: update` to extend the existing schema.

## Workflow

1. Sign in at `POST /api/auth/login`; bearer JWTs are signed and checked against revoked-token records. `POST /api/auth/logout` revokes the current token; the client clears its session.
2. Executives create shipments, upload/replace documents, edit and verify fields and invoice lines, resolve validation findings with a note, then submit with `POST /api/shipments/{id}/submit`.
3. Managers can inspect submitted shipments and manager-only reports, then approve or reject with a reason.
4. Only approved shipments can be exported. The built-in export is generic shipment field data; no customs filing-software layout is included. Configure a mapping before relying on its columns for another system.

Executives can only access shipments assigned to their account. Manager-only APIs enforce role checks on the backend. Uploads accept PDF/JPG/PNG up to 25 MB; repeated identical uploads are idempotent, while corrected uploads are retained as separate versions. The OpenAI adapter sends PDFs/images to the configured Responses endpoint with `store=false`; provider failures are recorded and routed to manual review. The fallback can read text PDFs and simple key/value text, but it does not provide OCR for scanned files.
