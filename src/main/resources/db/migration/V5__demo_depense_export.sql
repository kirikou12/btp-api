-- Demo dataset imported from src/main/resources/jdd/depense_export.csv.
-- Compatible with PostgreSQL and H2: no explicit identity values and no vendor-specific upsert syntax.

insert into users (email, password_hash, full_name, role, created_at, updated_at)
select 'demo@btp.local', '$2y$10$kGxeXDCCJaci4VVp3XU.aehE74naStAG1oDu.vJuAkYxhMrHvmuHS', 'Demo Manager', 'ADMIN', current_timestamp, current_timestamp
where not exists (select 1 from users where email = 'demo@btp.local');

update stage_templates
set sort_order = sort_order + 100, is_active = false, updated_at = current_timestamp
where name not in ('Terrain', 'Plan', 'Fondation', 'Elevation');

update stage_templates set sort_order = 1, is_active = true, updated_at = current_timestamp where name = 'Terrain';
insert into stage_templates (name, sort_order, is_active, created_at, updated_at)
select 'Terrain', 1, true, current_timestamp, current_timestamp
where not exists (select 1 from stage_templates where name = 'Terrain');
update stage_templates set sort_order = 2, is_active = true, updated_at = current_timestamp where name = 'Plan';
insert into stage_templates (name, sort_order, is_active, created_at, updated_at)
select 'Plan', 2, true, current_timestamp, current_timestamp
where not exists (select 1 from stage_templates where name = 'Plan');
update stage_templates set sort_order = 3, is_active = true, updated_at = current_timestamp where name = 'Fondation';
insert into stage_templates (name, sort_order, is_active, created_at, updated_at)
select 'Fondation', 3, true, current_timestamp, current_timestamp
where not exists (select 1 from stage_templates where name = 'Fondation');
update stage_templates set sort_order = 4, is_active = true, updated_at = current_timestamp where name = 'Elevation';
insert into stage_templates (name, sort_order, is_active, created_at, updated_at)
select 'Elevation', 4, true, current_timestamp, current_timestamp
where not exists (select 1 from stage_templates where name = 'Elevation');

-- Categories from direct rows, the advance invoice, and prepayed consumption rows.
insert into expense_categories (name, type, is_system, created_at, updated_at)
select v.name, v.type, false, current_timestamp, current_timestamp
from (values
    ('Achat', 'MISC'),
    ('Gravier', 'MATERIAL'),
    ('Divers', 'MATERIAL'),
    ('Fer', 'MATERIAL'),
    ('Sable', 'MATERIAL'),
    ('Eau', 'MATERIAL'),
    ('Tax', 'SERVICE'),
    ('Ciment', 'MATERIAL'),
    ('FER 4,5', 'MATERIAL'),
    ('FER 10', 'MATERIAL'),
    ('FER 12', 'MATERIAL'),
    ('CIMENT', 'MATERIAL'),
    ('رامبله', 'MATERIAL'),
    ('Point 7', 'MATERIAL'),
    ('Gaz', 'MATERIAL')
) as v(name, type)
where not exists (select 1 from expense_categories c where c.name = v.name)
;

-- Suppliers from Avance rows only.
insert into suppliers (name, phone, email, address, notes, created_at, updated_at)
select v.name, null, null, null, 'Import depense_export.csv', current_timestamp, current_timestamp
from (values
    ('BINAA')
) as v(name)
where not exists (select 1 from suppliers s where s.name = v.name)
;

-- Workers from payment rows.
insert into workers (name, type, planned_budget, created_at, updated_at)
select v.name, v.type, 0.00, current_timestamp, current_timestamp
from (values
    ('مهندس التخطيط', 'OTHER'),
    ('Maçcon', 'MASON'),
    ('Fatimetou Yihdhih', 'OTHER')
) as v(name, type)
where not exists (select 1 from workers w where w.name = v.name and w.type = v.type)
;

insert into projects (name, location, description, start_date, estimated_sale_price, budget, status, created_at, updated_at)
select 'Demo depenses CSV', 'Nouakchott', 'Projet de demo importe depuis depense_export.csv', DATE '2026-01-21', 6440501.80, 6440501.80, 'IN_PROGRESS', current_timestamp, current_timestamp
where not exists (select 1 from projects where name = 'Demo depenses CSV');

-- Construction stages for the demo project.
insert into construction_stages (project_id, name, sort_order, status, start_date, end_date, planned_budget, created_at, updated_at, stage_template_id, progress_percent)
select p.id, v.name, v.sort_order, v.status, DATE '2026-01-21', case when v.status = 'COMPLETED' then DATE '2026-04-12' else null end, 0.00, current_timestamp, current_timestamp, st.id, v.progress_percent
from (values
    ('Terrain', 1, 'COMPLETED', 100),
    ('Plan', 2, 'COMPLETED', 100),
    ('Fondation', 3, 'COMPLETED', 100),
    ('Elevation', 4, 'IN_PROGRESS', 50)
) as v(name, sort_order, status, progress_percent)
join (select id from projects where name = 'Demo depenses CSV') p on 1 = 1
join stage_templates st on st.name = v.name
where not exists (select 1 from construction_stages cs where cs.project_id = p.id and cs.name = v.name)
;

-- Direct expenses.
insert into direct_expenses (project_id, stage_id, category_id, supplier_id, amount, description, expense_date, created_at, updated_at, sub_category, document_ref)
select p.id, cs.id, c.id, null, v.amount, v.description, v.expense_date, current_timestamp, current_timestamp, null, null
from (values
    ('Terrain', 'Achat', 5200000.00, 'شراء النيمرو', DATE '2026-01-21'),
    ('Fondation', 'Gravier', 31842.80, 'اكرافي', DATE '2026-02-26'),
    ('Fondation', 'Divers', 6000.00, 'زنك | 56 | بكر 20 اصفر - حانوت اهل اسالك بني البطانه', DATE '2026-02-27'),
    ('Fondation', 'Fer', 70400.00, 'دريكه 12 تركي | دريكه 10 تركي | دريكه 6 ملم | خيل بناكي | رول بناكو | البوت 7 | رول زافو - BINAA', DATE '2026-02-28'),
    ('Fondation', 'Sable', 1300.00, 'التراب - Abdellahi Cissé', DATE '2026-03-02'),
    ('Fondation', 'Divers', 6000.00, 'Abdellahi Bedda', DATE '2026-03-02'),
    ('Fondation', 'Eau', 1600.00, 'الماء - Idoumou Ahmed Lely', DATE '2026-03-02'),
    ('Fondation', 'Tax', 5000.00, 'اذن بناء 22268622', DATE '2026-03-03'),
    ('Fondation', 'Eau', 2000.00, 'الماء - Hamadinou Ena', DATE '2026-03-03'),
    ('Fondation', 'Tax', 5000.00, '50000 كاش - اذن بناء', DATE '2026-03-03'),
    ('Fondation', 'Eau', 2000.00, 'الماء - Hamadinou Ena', DATE '2026-03-03'),
    ('Fondation', 'Ciment', 24000.00, 'CIMENT 42.5 - BINAA', DATE '2026-03-05'),
    ('Fondation', 'Divers', 14000.00, '500 x 28', DATE '2026-03-09'),
    ('Fondation', 'Divers', 11200.00, '400 x 28', DATE '2026-03-11'),
    ('Fondation', 'Sable', 1300.00, 'Abdellahi Cissé', DATE '2026-03-12'),
    ('Fondation', 'Tax', 2000.00, 'Redevance municipale - Commune du Ksar', DATE '2026-03-13'),
    ('Fondation', 'رامبله', 20000.00, 'رامبله 20608282', DATE '2026-03-14'),
    ('Fondation', 'رامبله', 40000.00, 'باقي حساب رامبله 36212121', DATE '2026-03-17'),
    ('Fondation', 'Point 7', 300.00, 'Mohamed Abdoul Jelil', DATE '2026-03-18'),
    ('Fondation', 'Gaz', 500.00, '10L | غاز Ets. Construction', DATE '2026-03-19'),
    ('Elevation', 'Divers', 25500.00, 'Lin - 1500', DATE '2026-03-24'),
    ('Elevation', 'Point 7', 300.00, 'Mohamed Abdoul Jelil', DATE '2026-03-24'),
    ('Elevation', 'Divers', 17000.00, '1000 x 17', DATE '2026-03-28'),
    ('Elevation', 'Sable', 1500.00, 'Abdellahi Cissé', DATE '2026-03-28'),
    ('Elevation', 'Gaz', 500.00, '10L | غاز Ets. Construction', DATE '2026-04-04'),
    ('Elevation', 'Eau', 2000.00, 'الماء Hamadinou Ena', DATE '2026-04-06'),
    ('Elevation', 'Divers', 17000.00, '1000 x 17', DATE '2026-04-06'),
    ('Elevation', 'Fer', 4320.00, 'دريكه 14 تركي | بار 6 ملم', DATE '2026-04-07'),
    ('Elevation', 'Sable', 1500.00, 'التراب Abdellahi Cissé', DATE '2026-04-07'),
    ('Elevation', 'Point 7', 400.00, '46585079', DATE '2026-04-08'),
    ('Elevation', 'Eau', 1329.00, 'الماء SNDE', DATE '2026-04-12')
) as v(stage_name, category_name, amount, description, expense_date)
join (select id from projects where name = 'Demo depenses CSV') p on 1 = 1
join construction_stages cs on cs.project_id = p.id and cs.name = v.stage_name
join expense_categories c on c.name = v.category_name
;

-- Worker payments. The CSV description is stored in document_ref because the current schema has no dedicated payment description column.
insert into worker_payments (worker_id, stage_id, amount, payment_date, document_ref, created_at, updated_at)
select w.id, cs.id, v.amount, v.payment_date, nullif(v.document_ref, ''), current_timestamp, current_timestamp
from (values
    ('مهندس التخطيط', 'OTHER', 'Plan', 5500.00, DATE '2026-01-21', 'دفعة لمهندس التخطيط'),
    ('مهندس التخطيط', 'OTHER', 'Plan', 8000.00, DATE '2026-02-04', 'دفعة لمهندس التخطيط'),
    ('Maçcon', 'MASON', 'Fondation', 60000.00, DATE '2026-02-27', 'مصوه'),
    ('Maçcon', 'MASON', 'Fondation', 40000.00, DATE '2026-03-02', 'مصوه 36212121'),
    ('Maçcon', 'MASON', 'Fondation', 20000.00, DATE '2026-03-25', 'مصوه نهاية الاساس 36971452'),
    ('Maçcon', 'MASON', 'Elevation', 25000.00, DATE '2026-03-25', 'مصوه 36212121 من فظة élévation.'),
    ('Maçcon', 'MASON', 'Elevation', 10000.00, DATE '2026-04-02', 'مصوه 47311238 من فظة élévation'),
    ('Fatimetou Yihdhih', 'OTHER', 'Elevation', 4000.00, DATE '2026-04-07', ''),
    ('Maçcon', 'MASON', 'Elevation', 10000.00, DATE '2026-04-11', 'مصوه 32788940 نهاية مرحلة élévation')
) as v(worker_name, worker_type, stage_name, amount, payment_date, document_ref)
join (select id from projects where name = 'Demo depenses CSV') p on 1 = 1
join workers w on w.name = v.worker_name and w.type = v.worker_type
join construction_stages cs on cs.project_id = p.id and cs.name = v.stage_name
;

-- One supplier advance from the single Avance row. Prepayed rows are consumptions, not invoices.
insert into supplier_invoices (supplier_id, project_id, reference, invoice_date, total_amount, currency, notes, status, created_at, updated_at, document_ref)
select s.id, p.id, v.reference, v.invoice_date, v.total_amount, 'MRU', v.notes, 'CONFIRMED', current_timestamp, current_timestamp, null
from (values
    ('DEMO-ADV-018', 'BINAA', 'Fondation', DATE '2026-03-09', 572000.00, 'FER 4,5 (qt 10, p 2400) | FER 10 (qt 40, p 3950) | FER 12 (qt 40, p 3800) | CIMENT (qt 800, p 297,1)')
) as v(reference, supplier_name, stage_name, invoice_date, total_amount, notes)
join (select id from projects where name = 'Demo depenses CSV') p on 1 = 1
join suppliers s on s.name = v.supplier_name
;

-- Supplier advance items parsed from the single Avance row.
insert into supplier_invoice_items (invoice_id, category_id, description, quantity, unit, unit_price, total_amount, created_at, updated_at)
select si.id, c.id, v.category_name, v.quantity, null, v.unit_price, v.total_amount, current_timestamp, current_timestamp
from (values
    ('DEMO-ADV-018', 'Fondation', 'FER 4,5', 10.00, 2400.00, 24000.00, DATE '2026-03-09', 'FER 4,5 (qt 10, p 2400) | FER 10 (qt 40, p 3950) | FER 12 (qt 40, p 3800) | CIMENT (qt 800, p 297,1)'),
    ('DEMO-ADV-018', 'Fondation', 'FER 10', 40.00, 3950.00, 158000.00, DATE '2026-03-09', 'FER 4,5 (qt 10, p 2400) | FER 10 (qt 40, p 3950) | FER 12 (qt 40, p 3800) | CIMENT (qt 800, p 297,1)'),
    ('DEMO-ADV-018', 'Fondation', 'FER 12', 40.00, 3800.00, 152000.00, DATE '2026-03-09', 'FER 4,5 (qt 10, p 2400) | FER 10 (qt 40, p 3950) | FER 12 (qt 40, p 3800) | CIMENT (qt 800, p 297,1)'),
    ('DEMO-ADV-018', 'Fondation', 'CIMENT', 800.00, 297.10, 237680.00, DATE '2026-03-09', 'FER 4,5 (qt 10, p 2400) | FER 10 (qt 40, p 3950) | FER 12 (qt 40, p 3800) | CIMENT (qt 800, p 297,1)')
) as v(invoice_reference, stage_name, category_name, quantity, unit_price, total_amount, invoice_date, notes)
join supplier_invoices si on si.reference = v.invoice_reference
join expense_categories c on c.name = v.category_name
;

-- Material consumptions from Prepayed rows only, deducted from the single advance invoice items.
insert into material_consumptions (invoice_item_id, project_id, stage_id, category_id, quantity_used, amount_used, consumption_date, notes, created_at, updated_at)
select sii.id, p.id, cs.id, c.id, v.quantity, v.total_amount, v.consumption_date, v.notes, current_timestamp, current_timestamp
from (values
    ('Fondation', 'CIMENT', 20.00, 300.00, 6000.00, DATE '2026-03-24', 'CIMENT (qt 20, p 300)'),
    ('Fondation', 'CIMENT', 60.00, 297.10, 17826.00, DATE '2026-03-24', 'CIMENT (qt 60, p 297,1)'),
    ('Fondation', 'FER 10', 2.00, 3950.00, 7900.00, DATE '2026-03-24', 'FER 10 (qt 2, p 3950) | FER 12 (qt 5, p 3800)'),
    ('Fondation', 'FER 12', 5.00, 3800.00, 19000.00, DATE '2026-03-24', 'FER 10 (qt 2, p 3950) | FER 12 (qt 5, p 3800)'),
    ('Elevation', 'FER 4,5', 2.00, 2400.00, 4800.00, DATE '2026-03-27', 'FER 4,5 (qt 2, p 2400)'),
    ('Elevation', 'CIMENT', 80.00, 297.50, 23800.00, DATE '2026-04-07', 'CIMENT (qt 80, 297,5 )'),
    ('Elevation', 'FER 4,5', 5.00, 2400.00, 12000.00, DATE '2026-04-07', 'FER 4,5 (qt 5, p 2400) | FER 10 (qt 5, 3950) | FER 12 (qt 5, p 3800)'),
    ('Elevation', 'FER 10', 5.00, 3950.00, 19750.00, DATE '2026-04-07', 'FER 4,5 (qt 5, p 2400) | FER 10 (qt 5, 3950) | FER 12 (qt 5, p 3800)'),
    ('Elevation', 'FER 12', 5.00, 3800.00, 19000.00, DATE '2026-04-07', 'FER 4,5 (qt 5, p 2400) | FER 10 (qt 5, 3950) | FER 12 (qt 5, p 3800)'),
    ('Elevation', 'CIMENT', 100.00, 297.50, 29750.00, DATE '2026-04-07', 'CIMENT (qt 100, p 297,5) | FER 12 (qt 2, p 3800)'),
    ('Elevation', 'FER 12', 2.00, 3800.00, 7600.00, DATE '2026-04-07', 'CIMENT (qt 100, p 297,5) | FER 12 (qt 2, p 3800)')
) as v(stage_name, category_name, quantity, unit_price, total_amount, consumption_date, notes)
join (select id from projects where name = 'Demo depenses CSV') p on 1 = 1
join supplier_invoices si on si.reference = 'DEMO-ADV-018' and si.project_id = p.id
join expense_categories c on c.name = v.category_name
join supplier_invoice_items sii on sii.invoice_id = si.id and sii.category_id = c.id
join construction_stages cs on cs.project_id = p.id and cs.name = v.stage_name
;
