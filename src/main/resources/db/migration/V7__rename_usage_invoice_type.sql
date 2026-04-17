update supplier_invoices
set invoice_type = 'SUPPLY_USAGE'
where invoice_type = 'USAGE';
