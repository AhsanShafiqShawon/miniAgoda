create table refresh_tokens(
    id uuid primary key, 
    token varchar(64) not null unique,
    revoked boolean not null default false,
    expires_at timestamp not null,
    created_at timestamp not null,
    user_id uuid not null,
    
    constraint fk_refresh_tokens_user foreign key (user_id) references users(id)
);