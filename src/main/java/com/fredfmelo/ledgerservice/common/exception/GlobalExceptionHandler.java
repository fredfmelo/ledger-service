package com.fredfmelo.ledgerservice.common.exception;

import java.time.Instant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fredfmelo.eventdrivencore.exception.BusinessException;
import com.fredfmelo.eventdrivencore.exception.TechnicalException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.error(ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                ex.getHttpStatusCode(),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatusCode.valueOf(ex.getHttpStatusCode()))
                .body(response);
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ErrorResponse> handleTechnical(TechnicalException ex) {
        log.error(ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                ex.getHttpStatusCode(),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatusCode.valueOf(ex.getHttpStatusCode()))
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, "Invalid request parameter"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {

        log.error("Unhandled exception type: {}", ex.getClass().getName(), ex);

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                500,
                "Internal server error");

        return ResponseEntity
                .internalServerError()
                .body(response);
    }
}
