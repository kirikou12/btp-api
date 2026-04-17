create table worker_stage_budgets (
    id bigserial primary key,
    worker_id bigint not null references workers(id) on delete cascade,
    stage_id bigint not null references construction_stages(id),
    planned_budget numeric(19, 2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_worker_stage_budget unique (worker_id, stage_id)
);

create index idx_worker_stage_budgets_worker on worker_stage_budgets(worker_id);
create index idx_worker_stage_budgets_stage on worker_stage_budgets(stage_id);
