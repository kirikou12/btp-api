create table stage_templates (
    id bigserial primary key,
    name varchar(255) not null unique,
    sort_order integer not null,
    is_active boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into stage_templates (name, sort_order, is_active, created_at, updated_at) values
    ('Foundation', 1, true, current_timestamp, current_timestamp),
    ('Elevation', 2, true, current_timestamp, current_timestamp),
    ('Roofing', 3, true, current_timestamp, current_timestamp),
    ('Plumbing', 4, true, current_timestamp, current_timestamp),
    ('Electricity', 5, true, current_timestamp, current_timestamp),
    ('Painting', 6, true, current_timestamp, current_timestamp),
    ('Finishing', 7, true, current_timestamp, current_timestamp);

alter table construction_stages add column stage_template_id bigint references stage_templates(id);
alter table construction_stages add column progress_percent integer not null default 0;

update construction_stages set stage_template_id = (select id from stage_templates where name = 'Foundation') where lower(name) = 'foundation';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Elevation') where lower(name) = 'elevation';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Roofing') where lower(name) = 'roofing';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Plumbing') where lower(name) = 'plumbing';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Electricity') where lower(name) = 'electricity';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Painting') where lower(name) = 'painting';
update construction_stages set stage_template_id = (select id from stage_templates where name = 'Finishing') where lower(name) = 'finishing';

alter table direct_expenses add column sub_category varchar(255);
alter table direct_expenses add column document_ref varchar(500);

alter table supplier_invoices add column document_ref varchar(500);
