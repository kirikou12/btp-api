alter table supplier_invoices
    add column invoice_type varchar(20) not null default 'SUPPLY';

update supplier_invoices set invoice_type = 'SUPPLY' where invoice_type is null;

alter table supplier_invoices
    add column source_supply_invoice_id bigint references supplier_invoices(id);

alter table supplier_invoices
    add column stage_id bigint references construction_stages(id);

alter table supplier_invoice_items
    add column source_supply_item_id bigint references supplier_invoice_items(id);

create table legacy_usage_groups as
select
    row_number() over (order by source_invoice_id, project_id, stage_id, consumption_date, note_group_key) as legacy_group_id,
    source_invoice_id,
    project_id,
    stage_id,
    consumption_date,
    note_group_key,
    display_notes,
    sum(amount_used) as total_amount
from (
    select
        source_item.invoice_id as source_invoice_id,
        consumption.project_id,
        consumption.stage_id,
        consumption.consumption_date,
        case
            when nullif(trim(consumption.notes), '') is null then 'consumption:' || cast(consumption.id as varchar)
            else lower(trim(consumption.notes))
        end as note_group_key,
        nullif(trim(consumption.notes), '') as display_notes,
        consumption.amount_used
    from material_consumptions consumption
    join supplier_invoice_items source_item on source_item.id = consumption.invoice_item_id
) grouped
group by source_invoice_id, project_id, stage_id, consumption_date, note_group_key, display_notes;

alter table legacy_usage_groups add column new_invoice_id bigint;

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
    source_invoice.supplier_id,
    legacy_group.project_id,
    'USAGE',
    legacy_group.source_invoice_id,
    legacy_group.stage_id,
    'LEGACY-USAGE-' || cast(legacy_group.legacy_group_id as varchar),
    legacy_group.consumption_date,
    legacy_group.total_amount,
    source_invoice.currency,
    legacy_group.display_notes,
    null,
    'CONFIRMED',
    current_timestamp,
    current_timestamp
from legacy_usage_groups legacy_group
join supplier_invoices source_invoice on source_invoice.id = legacy_group.source_invoice_id;

update legacy_usage_groups legacy_group
set new_invoice_id = usage_invoice.id
from supplier_invoices usage_invoice
where usage_invoice.reference = 'LEGACY-USAGE-' || cast(legacy_group.legacy_group_id as varchar)
  and usage_invoice.invoice_type = 'USAGE';

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
    legacy_group.new_invoice_id,
    consumption.invoice_item_id,
    consumption.category_id,
    source_item.description,
    coalesce(consumption.quantity_used, consumption.amount_used / nullif(source_item.unit_price, 0), 0),
    source_item.unit,
    source_item.unit_price,
    consumption.amount_used,
    current_timestamp,
    current_timestamp
from material_consumptions consumption
join supplier_invoice_items source_item on source_item.id = consumption.invoice_item_id
join legacy_usage_groups legacy_group on legacy_group.source_invoice_id = source_item.invoice_id
    and legacy_group.project_id = consumption.project_id
    and legacy_group.stage_id = consumption.stage_id
    and legacy_group.consumption_date = consumption.consumption_date
    and legacy_group.note_group_key = case
        when nullif(trim(consumption.notes), '') is null then 'consumption:' || cast(consumption.id as varchar)
        else lower(trim(consumption.notes))
    end;

drop table legacy_usage_groups;
