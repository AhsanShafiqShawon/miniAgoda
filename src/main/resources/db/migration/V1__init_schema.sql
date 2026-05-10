create table hotels(
    id uuid primary key, 
    name varchar(255) not null,
    address varchar(255) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table room_type(
    id uuid primary key,
    name varchar(255) not null,
    total_rooms integer not null,
    price decimal(19, 2) not null,
    hotel_id uuid not null references hotels(id),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table inventories(
    id uuid primary key,
    rooms_available integer not null,
    date date not null,
    room_type_id uuid not null references room_type(id),
    created_at timestamp not null,
    updated_at timestamp not null
);