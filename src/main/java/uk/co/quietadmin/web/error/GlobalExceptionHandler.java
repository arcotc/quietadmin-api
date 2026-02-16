package uk.co.quietadmin.web.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --------------------------------------------
    // Validation errors (@Valid)
    // --------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<FieldErrorDetail> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                request.getRequestURI(),
                details
        );

        logError(ex, error);

        return ResponseEntity.badRequest().body(error);
    }

    private FieldErrorDetail mapFieldError(FieldError fieldError) {
        return new FieldErrorDetail(
                fieldError.getField(),
                fieldError.getDefaultMessage()
        );
    }

    // --------------------------------------------
    // Illegal argument (bad input)
    // --------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI(),
                null
        );

        logError(ex, error);

        return ResponseEntity.badRequest().body(error);
    }


    // --------------------------------------------
    // Domain ApiException
    // --------------------------------------------
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {

        ApiError error = new ApiError(
                Instant.now(),
                ex.getStatus(),
                ex.getError(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );

        logError(ex, error);

        return ResponseEntity.status(ex.getStatus()).body(error);
    }


    // --------------------------------------------
    // Fallback (unexpected errors)
    // --------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        );

        logError(ex, error);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(
            Exception ex,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.UNAUTHORIZED,
                "Authentication failed",
                request.getRequestURI(),
                null
        );

        logError(ex, error);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        logError(ex);

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(Instant.now(), 403, ErrorCode.FORBIDDEN, ex.getMessage(), request.getRequestURI(), null));
    }

    private static void logError(Exception ex) {
        logError(ex, null);
    }

    private static void logError(Exception ex, ApiError error) {
        log.error("An unexpected error occurred", ex);
        if (error != null) {
            log.info("Error details: {}", error);
        }
    }
}