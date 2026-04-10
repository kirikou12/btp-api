# btp-api

Spring Boot 3.3 / Java 21 API for tracking BTP projects, direct expenses, supplier invoices, material consumption, dashboards, and reports.

## Features

- JWT auth with register, login, and `me`
- CRUD endpoints for projects, stage templates, project stages, categories, suppliers, expenses, invoices, and consumptions
- Business rules for invoice reconciliation and over-consumption prevention
- Local image upload endpoint for chantier receipts and supplier invoice photos
- Flyway database migration
- Demo seed data
- Swagger UI at `/swagger-ui.html`

## Local Run

```bash
./mvnw spring-boot:run
```

## Docker

Build the production image from the `btp-api` directory:

```bash
docker build -t btp-api .
```

Run it with the `prod` profile and PostgreSQL settings:

```bash
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/btp \
  -e DB_USERNAME=btp \
  -e DB_PASSWORD=btp \
  -v "$(pwd)/data:/app/data" \
  btp-api
```

The image stores uploaded files under `/app/data/uploads`, so mounting `/app/data` keeps documents persistent across container restarts.

On startup in non-`prod` profiles, the API seeds realistic demo data automatically when the database is empty:

- 1 in-progress project
- global stage templates and project-stage instances
- 3 suppliers
- supplier invoices with partially consumed materials
- direct expenses and material consumptions for dashboards and reports

Demo login:

- `demo@btp.local`
- `demo1234`

## Profiles

- default/dev: H2 in PostgreSQL mode
- `prod`: PostgreSQL using `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`

## Document uploads

- `POST /api/uploads/images` accepts a multipart image file named `file`
- Uploaded files are stored locally under `APP_DOCUMENTS_UPLOAD_DIR`
- If `APP_DOCUMENTS_UPLOAD_DIR` is not set, the API uses `./data/uploads`
- Saved records keep the returned path such as `/api/uploads/<generated-file-name>`

## Core business rules

- Supplier advances are stored as `SupplierInvoice` and `SupplierInvoiceItem`, but they do not count as chantier cost.
- Real cost is recognized only through `DirectExpense` and `MaterialConsumption`.
- New projects initialize their stages from the active global `StageTemplate` list.
