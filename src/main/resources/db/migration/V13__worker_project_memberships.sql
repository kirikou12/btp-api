create table worker_project_assignments (
    worker_id bigint not null references workers(id) on delete cascade,
    project_id bigint not null references projects(id) on delete cascade,
    created_at timestamp with time zone not null default current_timestamp,
    primary key (worker_id, project_id)
);

insert into worker_project_assignments (worker_id, project_id)
select w.id, w.project_id
from workers w
where w.project_id is not null
  and not exists (
      select 1
      from worker_project_assignments existing
      where existing.worker_id = w.id
        and existing.project_id = w.project_id
  );

insert into worker_project_assignments (worker_id, project_id)
select distinct wp.worker_id, cs.project_id
from worker_payments wp
join construction_stages cs on cs.id = wp.stage_id
where not exists (
    select 1
    from worker_project_assignments existing
    where existing.worker_id = wp.worker_id
      and existing.project_id = cs.project_id
);

create index idx_worker_project_assignments_project on worker_project_assignments(project_id);
