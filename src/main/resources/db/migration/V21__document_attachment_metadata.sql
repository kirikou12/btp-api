alter table supplier_invoice_images add column public_id text;
alter table supplier_invoice_images add column original_file_name varchar(255);
alter table supplier_invoice_images add column content_type varchar(100);
alter table supplier_invoice_images add column size_bytes bigint;
alter table supplier_invoice_images drop constraint if exists uk_supplier_invoice_images_order;

alter table worker_payment_images add column public_id text;
alter table worker_payment_images add column original_file_name varchar(255);
alter table worker_payment_images add column content_type varchar(100);
alter table worker_payment_images add column size_bytes bigint;
alter table worker_payment_images drop constraint if exists uk_worker_payment_images_order;

alter table supplier_invoices drop column if exists document_ref;
alter table worker_payments drop column if exists document_ref;
