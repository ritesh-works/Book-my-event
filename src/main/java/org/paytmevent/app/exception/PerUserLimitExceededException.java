package org.paytmevent.app.exception;

public class PerUserLimitExceededException extends RuntimeException {
    public PerUserLimitExceededException(String message) {
        super(message);
    }
}
