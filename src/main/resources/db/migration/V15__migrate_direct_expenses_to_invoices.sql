insert into supplier_invoices (
    supplier_id,
    project_id,
    invoice_type,
    source_supply_invoice_id,
    stage_id,
    reference,
    invoice_date,
    total_amount,
    currency,
    notes,
    document_ref,
    status,
    created_at,
    updated_at
)
select
    null,
    d.project_id,
    'DIRECT_EXPENSE',
    null,
    d.stage_id,
    'LEGACY-DIRECT-EXPENSE-' || cast(d.id as varchar),
    d.expense_date,
    d.amount,
    'MRU',
    d.description,
    d.document_ref,
    'CONFIRMED',
    d.created_at,
    d.updated_at
from direct_expenses d;

insert into supplier_invoice_items (
    invoice_id,
    source_supply_item_id,
    category_id,
    description,
    quantity,
    unit,
    unit_price,
    total_amount,
    created_at,
    updated_at
)
select
    si.id,
    null,
    d.category_id,
    d.description,
    case when c.type = 'MATERIAL' then 1.00 else null end,
    null,
    case when c.type = 'MATERIAL' then d.amount else null end,
    d.amount,
    d.created_at,
    d.updated_at
from direct_expenses d
join expense_categories c on c.id = d.category_id
join supplier_invoices si on si.reference = 'LEGACY-DIRECT-EXPENSE-' || cast(d.id as varchar)
    and si.invoice_type = 'DIRECT_EXPENSE';

drop table direct_expenses;
