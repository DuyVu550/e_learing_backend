package com.example.learning_backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
            OffsetDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getBindingResult().getAllErrors().isEmpty()
                ? "Validation failed"
                : ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
            OffsetDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * A body Jackson cannot bind — most often an enum value outside the accepted set, such as asking
     * to register with a role that is not selectable. Without this handler Spring answers a bare 403,
     * which reads as an authorization failure and sends callers looking in the wrong place.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        ApiError error = new ApiError(
            OffsetDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            rootMessage(ex),
            request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /** Jackson nests the useful detail; the outer message leaks package names and stack context. */
    private String rootMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String accepted = Arrays.stream(invalid.getTargetType().getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
            return "Invalid value '" + invalid.getValue() + "'; accepted values are: " + accepted;
        }
        return "Request body is malformed";
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
            OffsetDateTime.now(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}



