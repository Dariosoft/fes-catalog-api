package com.friendlyeshop.catalog.exception;

public class StockResultPublishException extends RuntimeException {

    public StockResultPublishException(String message) {
        super(message);
    }

    public StockResultPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
