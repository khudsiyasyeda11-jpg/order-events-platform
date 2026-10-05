package com.portfolio.orders.notification.messaging;

/** A message that can never be processed (malformed or semantically invalid). Not retried. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
