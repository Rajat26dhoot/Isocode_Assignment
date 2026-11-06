# Company A Customs Workspace

React 18 + TypeScript + Tailwind CSS workspace for the Customs Broker AI Document Extraction & Cross-Check Tool.

## Run

```bash
npm install
npm run dev
```

The app runs in a fully interactive demo mode by default using the seeded shipment data. Set `VITE_API_URL=http://localhost:8080/api` in `.env.local` to enable the Spring Boot API client. API calls gracefully fall back to the in-memory demo state when the backend is unavailable.

## Routes

- `/shipments` — shipment queue and KPI dashboard
- `/shipments/new` — three-document upload/dropzone flow
- `/shipments/:id/extract` — extraction and reconciliation progress
- `/shipments/:id/review` — side-by-side document review and field editor
- `/shipments/:id/approval` — manager gatekeeper and CSV/XLSX export
- `/analytics` — supplier accuracy and field audit history

The role switcher in the top bar toggles executive and manager experiences without requiring a login during evaluation. The API client supports JWT login and the backend endpoints documented in the Spring Boot service.
