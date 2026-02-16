package uk.co.quietadmin.web.error;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final ErrorCode error;

    public ApiException(int status, ErrorCode error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }
}