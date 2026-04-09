# btp-api

Spring Boot 3.3 / Java 21 API for tracking BTP projects, direct expenses, supplier invoices, material consumption, dashboards, and reports.

## Features

- JWT auth with register, login, and `me`
- CRUD endpoints for projects, stage templates, project stages, categories, suppliers, expenses, invoices, and consumptions
- Business rules for invoice reconciliation and over-consumption prevention
- Flyway database migration
- Demo seed data
- Swagger UI at `/swagger-ui.html`

## Local Run

```bash
./mvnw spring-boot:run
```

On startup in non-`prod` profiles, the API seeds realistic demo data automatically when the database is empty:

- 3 in-progress projects
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

## Core business rules

- Supplier advances are stored as `SupplierInvoice` and `SupplierInvoiceItem`, but they do not count as chantier cost.
- Real cost is recognized only through `DirectExpense` and `MaterialConsumption`.
- New projects initialize their stages from the active global `StageTemplate` list.
