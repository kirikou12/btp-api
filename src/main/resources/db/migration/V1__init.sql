create table users (
    id bigserial primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    full_name varchar(255) not null,
    role varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table projects (
    id bigserial primary key,
    name varchar(255) not null,
    location varchar(255) not null,
    description text,
    start_date date not null,
    estimated_sale_price numeric(19, 2) not null,
    budget numeric(19, 2) not null,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table expense_categories (
    id bigserial primary key,
    name varchar(255) not null unique,
    type varchar(50) not null,
    is_system boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table suppliers (
    id bigserial primary key,
    name varchar(255) not null,
    phone varchar(255),
    email varchar(255),
    address varchar(500),
    notes text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table construction_stages (
    id bigserial primary key,
    project_id bigint not null references projects(id) on delete cascade,
    name varchar(255) not null,
    sort_order integer not null,
    status varchar(50) not null,
    start_date date,
    end_date date,
    planned_budget numeric(19, 2),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table direct_expenses (
    id bigserial primary key,
    project_id bigint not null references projects(id) on delete cascade,
    stage_id bigint references construction_stages(id),
    category_id bigint not null references expense_categories(id),
    supplier_id bigint references suppliers(id),
    amount numeric(19, 2) not null,
    description varchar(500) not null,
    expense_date date not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table supplier_invoices (
    id bigserial primary key,
    supplier_id bigint not null references suppliers(id),
    project_id bigint references projects(id),
    reference varchar(255),
    invoice_date date not null,
    total_amount numeric(19, 2) not null,
    currency varchar(10) not null,
    notes text,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table supplier_invoice_items (
    id bigserial primary key,
    invoice_id bigint not null references supplier_invoices(id) on delete cascade,
    category_id bigint not null references expense_categories(id),
    description varchar(500) not null,
    quantity numeric(19, 2),
    unit varchar(50),
    unit_price numeric(19, 2),
    total_amount numeric(19, 2) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table material_consumptions (
    id bigserial primary key,
    invoice_item_id bigint not null references supplier_invoice_items(id),
    project_id bigint not null references projects(id) on delete cascade,
    stage_id bigint not null references construction_stages(id),
    category_id bigint not null references expense_categories(id),
    quantity_used numeric(19, 2),
    amount_used numeric(19, 2) not null,
    consumption_date date not null,
    notes text,
    created_at timestamp not null,
    updated_at timestamp not null
);
