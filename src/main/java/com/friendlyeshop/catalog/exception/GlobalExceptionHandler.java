package com.friendlyeshop.catalog.exception;

import com.friendlyeshop.catalog.model.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("Falta el parámetro obligatorio: " + exception.getParameterName());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBinding(BindException exception) {
        return badRequest("Los datos del producto son inválidos");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidType(MethodArgumentTypeMismatchException exception) {
        return badRequest("Los datos del producto son inválidos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return badRequest("Los datos del producto son inválidos");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("producto_no_encontrado", exception.getMessage()));
    }

    @ExceptionHandler(InvalidProductException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProduct(InvalidProductException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("conflicto_de_publicacion", exception.getMessage()));
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(StorageUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("almacenamiento_no_disponible", exception.getMessage()));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse("datos_invalidos", message));
    }
}
