package com.miniagoda.booking.exception;

public class InventoryIncompleteException extends RuntimeException {
    public InventoryIncompleteException() {
        super("Inventory missing for some dates");
    }
}