create index idx_supplier_invoices_project_type_date_id
    on supplier_invoices(project_id, invoice_type, invoice_date desc, id desc);

create index idx_supplier_invoices_stage
    on supplier_invoices(stage_id);

create index idx_supplier_invoices_source_supply
    on supplier_invoices(source_supply_invoice_id);

create index idx_supplier_invoices_type_date
    on supplier_invoices(invoice_type, invoice_date desc);

create index idx_supplier_invoice_items_invoice
    on supplier_invoice_items(invoice_id);

create index idx_supplier_invoice_items_source_supply
    on supplier_invoice_items(source_supply_item_id);

create index idx_supplier_invoice_items_category
    on supplier_invoice_items(category_id);

create index idx_construction_stages_project_sort
    on construction_stages(project_id, sort_order);

create unique index uk_construction_stages_project_name
    on construction_stages(project_id, name);

create index idx_worker_payments_worker_date
    on worker_payments(worker_id, payment_date desc);

create index idx_worker_payments_stage
    on worker_payments(stage_id);

alter table supplier_invoices
    add constraint chk_supplier_invoices_type
    check (invoice_type in ('SUPPLY', 'SUPPLY_USAGE', 'SUPPLY_RETURN', 'DIRECT_USAGE', 'DIRECT_EXPENSE'));

alter table supplier_invoices
    add constraint chk_supplier_invoices_status
    check (status in ('DRAFT', 'CONFIRMED', 'CLOSED'));

alter table supplier_invoices
    add constraint chk_supplier_invoices_total_non_negative
    check (total_amount >= 0);

alter table supplier_invoices
    add constraint chk_supplier_invoices_type_shape
    check (
        (
            invoice_type = 'SUPPLY'
            and supplier_id is not null
            and source_supply_invoice_id is null
            and stage_id is null
        )
        or (
            invoice_type = 'SUPPLY_USAGE'
            and supplier_id is not null
            and project_id is not null
            and stage_id is not null
            and source_supply_invoice_id is not null
        )
        or (
            invoice_type = 'SUPPLY_RETURN'
            and supplier_id is not null
            and source_supply_invoice_id is not null
            and stage_id is null
        )
        or (
            invoice_type = 'DIRECT_USAGE'
            and supplier_id is not null
            and project_id is not null
            and stage_id is not null
            and source_supply_invoice_id is null
        )
        or (
            invoice_type = 'DIRECT_EXPENSE'
            and project_id is not null
            and stage_id is not null
            and source_supply_invoice_id is null
        )
    );

alter table supplier_invoice_items
    add constraint chk_supplier_invoice_items_total_non_negative
    check (total_amount >= 0);

alter table construction_stages
    add constraint chk_construction_stages_status
    check (status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'BLOCKED'));

alter table construction_stages
    add constraint chk_construction_stages_progress
    check (progress_percent between 0 and 100);

alter table construction_stages
    add constraint chk_construction_stages_dates
    check (end_date is null or start_date is null or end_date >= start_date);

alter table worker_payments
    add constraint chk_worker_payments_amount_non_negative
    check (amount >= 0);

alter table worker_stage_budgets
    add constraint chk_worker_stage_budgets_planned_non_negative
    check (planned_budget >= 0);
