alter table inventories
add constraint chk_available_units_non_negative
check(available_units >= 0);