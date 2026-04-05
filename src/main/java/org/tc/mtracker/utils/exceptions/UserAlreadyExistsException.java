package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {
    public UserAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "user_already_exists", message);
    }
}
