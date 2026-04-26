# btp-api

Spring Boot 3.3 / Java 21 API for tracking BTP projects, supplier invoices, direct-expense invoices, usage invoices, dashboards, and reports.

## Features

- JWT auth with register, login, and `me`
- CRUD endpoints for projects, stage templates, project stages, categories, suppliers, and invoices
- Business rules for invoice reconciliation and usage/return availability prevention
- Cloudinary-backed image upload endpoint for chantier receipts and supplier invoice photos
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
  -e CLOUDINARY_CLOUD_NAME='your-cloud-name' \
  -e CLOUDINARY_API_KEY='your-api-key' \
  -e CLOUDINARY_API_SECRET='your-api-secret' \
  btp-api
```

Uploaded documents are stored in Cloudinary. The database only keeps the returned Cloudinary URL or a JSON array of URLs when an invoice or worker payment has multiple images.

Required production environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_SECURITY_JWT_SECRET`
- `APP_SECURITY_CORS_ALLOWED_ORIGINS`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

Optional production environment variables:

- `APP_SECURITY_JWT_EXPIRATION_MINUTES`
- `DB_POOL_MAX_SIZE`
- `DB_POOL_MIN_IDLE`
- `APP_DOCUMENTS_MAX_FILE_SIZE`
- `APP_DOCUMENTS_MAX_REQUEST_SIZE`
- `CLOUDINARY_FOLDER` (defaults to `btp/documents`)

On startup in non-`prod` profiles, the API seeds realistic demo data automatically when the database is empty:

- 1 in-progress project
- global stage templates and project-stage instances
- 3 suppliers
- supplier invoices with partially used materials
- direct-expense invoices and usage invoices for dashboards and reports

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
- Uploaded files are sent to Cloudinary and the response `path` is the secure Cloudinary URL
- Saved invoice/payment records keep one URL or a JSON array of URLs in `document_ref`
- `GET /api/uploads/{fileName}` remains available for legacy database-backed uploads created before Cloudinary

## Core business rules

- Supplier advances are stored as `SupplierInvoice` and `SupplierInvoiceItem`, but they do not count as chantier cost.
- Real cost is recognized through direct-expense invoices, usage invoices, and worker payments.
- Project stages are created and updated directly on each project.
