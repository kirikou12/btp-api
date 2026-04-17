alter table workers
    add column project_id bigint references projects(id);

update workers w
set project_id = worker_projects.project_id
from (
    select wp.worker_id, min(cs.project_id) as project_id
    from worker_payments wp
    join construction_stages cs on cs.id = wp.stage_id
    group by wp.worker_id
) worker_projects
where worker_projects.worker_id = w.id;
