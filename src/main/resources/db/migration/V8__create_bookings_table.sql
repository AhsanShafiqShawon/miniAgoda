create table bookings (
    id uuid primary key default gen_random_uuid(),
    room_type_id uuid not null,
    check_in date not null,
    check_out date not null,
    rooms_booked integer not null,
    total_price decimal not null,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null,

    constraint fk_bookings_room_type foreign key (room_type_id) references room_type(id)
);