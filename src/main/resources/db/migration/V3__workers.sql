create table workers (
    id bigserial primary key,
    name varchar(255) not null,
    type varchar(50) not null,
    planned_budget numeric(19, 2) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table worker_payments (
    id bigserial primary key,
    worker_id bigint not null references workers(id),
    stage_id bigint not null references construction_stages(id),
    amount numeric(19, 2) not null,
    payment_date date not null,
    document_ref varchar(500),
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into workers (name, type, planned_budget, created_at, updated_at)
select distinct
    coalesce(nullif(trim(d.description), ''), 'Migrated labor'),
    case upper(coalesce(d.sub_category, ''))
        when 'MASON' then 'MASON'
        when 'ELECTRICIAN' then 'ELECTRICIAN'
        when 'PLUMBER' then 'PLUMBER'
        else 'OTHER'
    end,
    0,
    current_timestamp,
    current_timestamp
from direct_expenses d
join expense_categories c on c.id = d.category_id
where c.type = 'LABOR'
  and coalesce(d.stage_id, (
      select cs.id
      from construction_stages cs
      where cs.project_id = d.project_id
      order by cs.sort_order
      limit 1
  )) is not null;

insert into worker_payments (worker_id, stage_id, amount, payment_date, document_ref, created_at, updated_at)
select
    w.id,
    coalesce(d.stage_id, (
        select cs.id
        from construction_stages cs
        where cs.project_id = d.project_id
        order by cs.sort_order
        limit 1
    )),
    d.amount,
    d.expense_date,
    d.document_ref,
    d.created_at,
    d.updated_at
from direct_expenses d
join expense_categories c on c.id = d.category_id
join workers w on w.name = coalesce(nullif(trim(d.description), ''), 'Migrated labor')
    and w.type = case upper(coalesce(d.sub_category, ''))
        when 'MASON' then 'MASON'
        when 'ELECTRICIAN' then 'ELECTRICIAN'
        when 'PLUMBER' then 'PLUMBER'
        else 'OTHER'
    end
where c.type = 'LABOR'
  and coalesce(d.stage_id, (
      select cs.id
      from construction_stages cs
      where cs.project_id = d.project_id
      order by cs.sort_order
      limit 1
  )) is not null;

delete from direct_expenses
where category_id in (select id from expense_categories where type = 'LABOR')
  and coalesce(stage_id, (
      select cs.id
      from construction_stages cs
      where cs.project_id = direct_expenses.project_id
      order by cs.sort_order
      limit 1
  )) is not null;
