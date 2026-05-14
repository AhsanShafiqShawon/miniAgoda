create table payments (
    id uuid primary key default gen_random_uuid(),
    booking_id uuid not null,
    amount decimal not null,
    currency varchar(10) not null,
    stripe_payment_intent_id varchar(255) not null,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null,

    constraint fk_payments_booking foreign key (booking_id) references bookings(id),
    constraint uq_payments_booking unique (booking_id)
);