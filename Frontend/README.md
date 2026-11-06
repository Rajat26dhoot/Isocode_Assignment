# Customs Operations Workspace

React 18 + TypeScript + Tailwind CSS workspace for the Customs Broker AI Document Extraction & Cross-Check Tool.

## Run

```bash
npm install
npm run dev
```

The frontend requires the Spring Boot backend at `http://localhost:8080/api` (override with `VITE_API_URL`). Sign in with an account provisioned in the backend. Shipment records, report data, review edits, and source document bytes are loaded from and saved through the backend; an API failure does not switch the app to fabricated local shipment data.

## Routes

- `/shipments` — shipment queue and KPI dashboard
- `/shipments/new` — three-document upload/dropzone flow
- `/shipments/:id/extract` — extraction and reconciliation progress
- `/shipments/:id/review` — side-by-side document review and field editor
- `/shipments/:id/approval` — manager gatekeeper and CSV/XLSX export
- `/analytics` — supplier accuracy and field audit history

Use the account menu to re-authenticate as a different role. The backend JWT determines access to approval actions.
