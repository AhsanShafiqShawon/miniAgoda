create table users (
    id uuid primary key default gen_random_uuid(),
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    email varchar(255) not null,
    password varchar(255) not null,
    role varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null,

    constraint uq_users_email unique (email)
);