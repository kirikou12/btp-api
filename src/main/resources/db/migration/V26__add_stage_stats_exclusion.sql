alter table construction_stages
    add column excluded_from_project_stats boolean not null default false;
