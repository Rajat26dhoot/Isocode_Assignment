# Company A Customs Document Extraction Backend

Spring Boot 3.3 / Java 17 backend for AI-assisted customs document extraction, three-way validation, audit history, manager approval, and CSV/XLSX filing exports.

## Run

The application expects Aiven MySQL variables:

```text
AIVEN_DB_HOST
AIVEN_DB_PORT
AIVEN_DB_NAME
AIVEN_DB_USER
AIVEN_DB_PASSWORD
JWT_SECRET                       # at least 32 bytes in production
AES_ENCRYPTION_KEY               # managed secret; used for AES-256-GCM importer contact encryption
```

Build with `mvn clean package`, then run `java -jar target/customs-document-extraction-1.0.0.jar`.

The datasource uses MySQL `sslMode=REQUIRED` and a Hikari pool of 5 connections with 2 idle connections and a 20 second connection timeout. Schema creation is set to `update` for first deployment and seed data is inserted only when the user table is empty.

## Seed login

`exec@companya.in` / `password123` (executive)  
`manager@companya.in` / `password123` (manager)

## Main API flow

1. `POST /api/auth/login` to get a JWT.
2. `GET /api/shipments` and `GET /api/shipments/{id}` to view the two seeded shipments.
3. `POST /api/shipments/{id}/documents/extract?docType=INVOICE|PACKING_LIST|BILL_OF_LADING` with multipart part `file` to extract a document.
4. `PATCH /api/shipments/fields/{fieldId}` or `PATCH /api/shipments/line-items/{lineItemId}` to make an audited human correction. Use `POST /api/shipments/documents/{documentId}/verify` for an unreadable document.
5. Resolve discrepancies using `PATCH /api/shipments/discrepancies/{id}`. `managerOverride=true` is manager-only.
6. A manager calls `POST /api/shipments/{id}/approve`; approved shipments can be downloaded with `GET /api/shipments/{id}/export?format=csv` or `format=xlsx`.

Approval/export returns HTTP 422 with all blocking reasons for low-confidence unverified fields, unreadable unverified documents, and open discrepancies.

## Extraction providers

The default `app.extraction.provider=fallback` parser handles text uploads containing `key: value` fields. Unsupported or unreadable uploads are routed to manual entry; the parser never substitutes sample shipment values. For image and PDF vision extraction, set `app.extraction.provider=openai` and `OPENAI_API_KEY`. The adapter sends PDF files as `input_file` and supported images as `input_image` to the Responses API, requests `store=false`, and parses returned structured fields. Source document bytes for newly uploaded files are persisted with each document so the review workspace can display the actual source file.
