package uk.co.quietadmin.web.error;

public record FieldErrorDetail(
        String field,
        String message
) {
}