create table email_verification_tokens(
    id uuid primary key, 
    token_hash varchar(64) not null unique,
    used boolean not null default false,
    expires_at timestamp not null,
    user_id uuid not null,
    
    constraint fk_email_verification_tokens_user foreign key (user_id) references users(id)
);