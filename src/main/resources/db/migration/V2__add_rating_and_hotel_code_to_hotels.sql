alter table hotels
    add column rating decimal(3, 1) check(rating >= 0.0 and rating <= 10.0),
    add column hotel_code varchar(255) not null unique;