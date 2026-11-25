package com.friendlyeshop.catalog.model.dto.out;

public record ErrorResponse(String error, String message) {
    public static ErrorResponse from(String error, String message) {
        return new ErrorResponse(error, message);
    }
}
