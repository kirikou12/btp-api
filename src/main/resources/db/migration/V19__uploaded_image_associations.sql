create table supplier_invoice_images (
    id bigserial primary key,
    invoice_id bigint not null references supplier_invoices(id) on delete cascade,
    image_url text not null,
    sort_order integer not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_supplier_invoice_images_order unique (invoice_id, sort_order)
);

create index idx_supplier_invoice_images_invoice on supplier_invoice_images(invoice_id);

create table worker_payment_images (
    id bigserial primary key,
    payment_id bigint not null references worker_payments(id) on delete cascade,
    image_url text not null,
    sort_order integer not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_worker_payment_images_order unique (payment_id, sort_order)
);

create index idx_worker_payment_images_payment on worker_payment_images(payment_id);

insert into supplier_invoice_images (invoice_id, image_url, sort_order, created_at, updated_at)
select
    si.id,
    nullif(trim(urls.image_url), ''),
    urls.sort_order - 1,
    si.created_at,
    si.updated_at
from supplier_invoices si
cross join lateral jsonb_array_elements_text(si.document_ref::jsonb) with ordinality as urls(image_url, sort_order)
where nullif(trim(si.document_ref), '') is not null
  and trim(si.document_ref) like '[%'
  and nullif(trim(urls.image_url), '') is not null;

insert into supplier_invoice_images (invoice_id, image_url, sort_order, created_at, updated_at)
select
    si.id,
    trim(both '"' from trim(si.document_ref)),
    0,
    si.created_at,
    si.updated_at
from supplier_invoices si
where nullif(trim(si.document_ref), '') is not null
  and trim(si.document_ref) not like '[%';

insert into worker_payment_images (payment_id, image_url, sort_order, created_at, updated_at)
select
    wp.id,
    nullif(trim(urls.image_url), ''),
    urls.sort_order - 1,
    wp.created_at,
    wp.updated_at
from worker_payments wp
cross join lateral jsonb_array_elements_text(wp.document_ref::jsonb) with ordinality as urls(image_url, sort_order)
where nullif(trim(wp.document_ref), '') is not null
  and trim(wp.document_ref) like '[%'
  and nullif(trim(urls.image_url), '') is not null;

insert into worker_payment_images (payment_id, image_url, sort_order, created_at, updated_at)
select
    wp.id,
    trim(both '"' from trim(wp.document_ref)),
    0,
    wp.created_at,
    wp.updated_at
from worker_payments wp
where nullif(trim(wp.document_ref), '') is not null
  and trim(wp.document_ref) not like '[%';
