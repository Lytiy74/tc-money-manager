package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class UserResetPasswordException extends ApiException {
    public UserResetPasswordException(String message) {
        super(HttpStatus.BAD_REQUEST, "password_reset_failed", message);
    }
}
