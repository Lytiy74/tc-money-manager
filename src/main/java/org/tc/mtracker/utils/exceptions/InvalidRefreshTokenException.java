package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", message);
    }
}
