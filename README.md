# btp-api

Spring Boot 3.3 / Java 21 API for tracking BTP projects, direct expenses, supplier invoices, material consumption, dashboards, and reports.

## Features

- JWT auth with register, login, and `me`
- CRUD endpoints for projects, stage templates, project stages, categories, suppliers, expenses, invoices, and consumptions
- Business rules for invoice reconciliation and over-consumption prevention
- Database-backed image upload endpoint for chantier receipts and supplier invoice photos
- Flyway database migration
- Demo seed data
- Swagger UI at `/swagger-ui.html`

## Local Run

```bash
./mvnw spring-boot:run
```

The API defaults to the `local` profile when no profile is set. Local mode uses:

- in-memory H2
- demo seed data
- Swagger UI and H2 console
- a local JWT secret fallback for development only

Useful local endpoints:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/h2-console`
- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/health/liveness`
- `http://localhost:8080/actuator/health/readiness`

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
  -e APP_SECURITY_JWT_SECRET='replace-with-a-long-random-secret' \
  -e APP_SECURITY_CORS_ALLOWED_ORIGINS='https://app.example.com,https://admin.example.com' \
  btp-api
```

Uploaded documents are stored in PostgreSQL, so they persist with the database and do not depend on a writable container directory.

Required production environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_SECURITY_JWT_SECRET`
- `APP_SECURITY_CORS_ALLOWED_ORIGINS`

Optional production environment variables:

- `APP_SECURITY_JWT_EXPIRATION_MINUTES`
- `DB_POOL_MAX_SIZE`
- `DB_POOL_MIN_IDLE`
- `APP_DOCUMENTS_MAX_FILE_SIZE`
- `APP_DOCUMENTS_MAX_REQUEST_SIZE`

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

- `local` (default): H2 in PostgreSQL mode
- `prod`: PostgreSQL using `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`

## Actuator

Actuator is enabled with a minimal public surface:

- exposed endpoints: `health`, `info`
- unauthenticated health probes: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- other actuator endpoints remain protected by Spring Security

## Document uploads

- `POST /api/uploads/images` accepts a multipart image file named `file`
- Uploaded files are stored in the `uploaded_documents` database table as binary content
- Saved records keep the returned path such as `/api/uploads/<generated-file-name>`

## Core business rules

- Supplier advances are stored as `SupplierInvoice` and `SupplierInvoiceItem`, but they do not count as chantier cost.
- Real cost is recognized only through `DirectExpense` and `MaterialConsumption`.
- New projects initialize their stages from the active global `StageTemplate` list.
