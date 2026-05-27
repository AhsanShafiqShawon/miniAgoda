package com.miniagoda.booking.exception;

public class NotEnoughRoomsException extends RuntimeException {
    public NotEnoughRoomsException() {
        super("Not enough rooms available");
    }
}