alter table room_type rename column total_rooms to capacity;
alter table room_type add column total_units integer not null;