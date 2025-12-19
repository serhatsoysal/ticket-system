package com.heditra.ticketservice.exception.handler;

import com.heditra.ticketservice.dto.common.ApiErrorResponse;
import com.heditra.ticketservice.exception.BusinessException;
import com.heditra.ticketservice.exception.TechnicalException;
import com.heditra.ticketservice.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception occurred: {}", ex.getMessage());
        
        ApiErrorResponse error = ApiErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        log.warn("Validation exception occurred: {}", ex.getMessage());
        
        ApiErrorResponse error = ApiErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ApiErrorResponse> handleTechnicalException(
            TechnicalException ex, HttpServletRequest request) {
        log.error("Technical exception occurred: {}", ex.getMessage(), ex);
        
        ApiErrorResponse error = ApiErrorResponse.of(
                ex.getErrorCode(),
                "A technical error occurred. Please try again later.",
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        
        ApiErrorResponse error = ApiErrorResponse.of(
                "VALIDATION_FAILED",
                "Validation failed for request",
                request.getRequestURI(),
                errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        
        ApiErrorResponse error = ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
