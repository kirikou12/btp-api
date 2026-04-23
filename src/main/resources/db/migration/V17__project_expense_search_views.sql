create view project_expense_entries as
select
    'invoice-' || cast(invoice.id as varchar) as id,
    'INVOICE' as source_kind,
    invoice.id as source_id,
    invoice.project_id as project_id,
    invoice.stage_id as stage_id,
    stage.name as stage_name,
    invoice.supplier_id as supplier_id,
    supplier.name as supplier_name,
    cast(null as bigint) as worker_id,
    cast(null as varchar) as worker_name,
    cast(null as varchar) as worker_type,
    invoice.invoice_type as invoice_type,
    invoice.invoice_date as expense_date,
    invoice.total_amount as amount,
    coalesce(
        nullif(invoice.notes, ''),
        nullif(invoice.reference, ''),
        (
            select category.name
            from supplier_invoice_items item
            join expense_categories category on category.id = item.category_id
            where item.invoice_id = invoice.id
            order by item.id
            fetch first 1 row only
        ),
        case
            when invoice.invoice_type = 'DIRECT_EXPENSE' then 'Direct Expense'
            when invoice.invoice_type = 'DIRECT_USAGE' then 'Direct Invoice'
            else 'Usage Invoice'
        end
    ) as category,
    coalesce(
        supplier.name,
        (
            select item.description
            from supplier_invoice_items item
            where item.invoice_id = invoice.id
            order by item.id
            fetch first 1 row only
        ),
        case
            when invoice.invoice_type = 'DIRECT_EXPENSE' then 'Direct Expense'
            when invoice.invoice_type = 'DIRECT_USAGE' then 'Direct Invoice'
            else 'Usage Invoice'
        end
    ) as description
from supplier_invoices invoice
join construction_stages stage on stage.id = invoice.stage_id
left join suppliers supplier on supplier.id = invoice.supplier_id
where invoice.project_id is not null
  and invoice.invoice_type in ('DIRECT_EXPENSE', 'DIRECT_USAGE', 'SUPPLY_USAGE')

union all

select
    'worker-payment-' || cast(payment.id as varchar) as id,
    'WORKER_PAYMENT' as source_kind,
    payment.id as source_id,
    stage.project_id as project_id,
    payment.stage_id as stage_id,
    stage.name as stage_name,
    cast(null as bigint) as supplier_id,
    cast(null as varchar) as supplier_name,
    worker.id as worker_id,
    worker.name as worker_name,
    worker.type as worker_type,
    cast(null as varchar) as invoice_type,
    payment.payment_date as expense_date,
    payment.amount as amount,
    'Labor - ' || worker.type as category,
    worker.name as description
from worker_payments payment
join workers worker on worker.id = payment.worker_id
join construction_stages stage on stage.id = payment.stage_id;

create view project_expense_entry_categories as
select distinct
    'invoice-' || cast(invoice.id as varchar) || '-' || cast(item.category_id as varchar) as id,
    'invoice-' || cast(invoice.id as varchar) as entry_id,
    item.category_id as category_id
from supplier_invoices invoice
join supplier_invoice_items item on item.invoice_id = invoice.id
where invoice.project_id is not null
  and invoice.invoice_type in ('DIRECT_EXPENSE', 'DIRECT_USAGE', 'SUPPLY_USAGE');

create index idx_supplier_invoices_project_stage_type_date_id
    on supplier_invoices(project_id, stage_id, invoice_type, invoice_date desc, id desc);

create index idx_worker_payments_stage_date_id
    on worker_payments(stage_id, payment_date desc, id desc);
