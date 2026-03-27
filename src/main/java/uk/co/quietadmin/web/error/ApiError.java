package uk.co.quietadmin.web.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        Integer status,
        ErrorCode error,
        String message,
        String path,
        List<FieldErrorDetail> details
) {
}