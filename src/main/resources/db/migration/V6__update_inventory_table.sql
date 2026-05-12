alter table inventories rename rooms_available to available_units;
alter table inventories add constraint uq_inventory_room_type_date unique (room_type_id, date);