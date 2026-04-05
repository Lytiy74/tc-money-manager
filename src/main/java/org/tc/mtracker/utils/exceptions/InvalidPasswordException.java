package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidPasswordException extends ApiException {
    public InvalidPasswordException(String message) {
        super(HttpStatus.BAD_REQUEST, "invalid_password", message);
    }
}
