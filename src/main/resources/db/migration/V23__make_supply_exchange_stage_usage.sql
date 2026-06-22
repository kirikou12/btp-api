alter table supplier_invoices
    drop constraint if exists chk_supplier_invoices_type_shape;

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
            invoice_type = 'SUPPLY_EXCHANGE'
            and supplier_id is not null
            and source_supply_invoice_id is not null
            and (
                (
                    project_id is null
                    and stage_id is null
                )
                or (
                    project_id is not null
                    and stage_id is not null
                )
            )
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
