create table uploaded_documents (
    id bigserial primary key,
    file_name varchar(255) not null unique,
    original_file_name varchar(255),
    content_type varchar(100) not null,
    size_bytes bigint not null,
    content bytea not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
