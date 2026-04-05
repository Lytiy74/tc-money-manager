package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class UserAlreadyActivatedException extends ApiException {
    public UserAlreadyActivatedException(String message) {
        super(HttpStatus.CONFLICT, "user_already_activated", message);
    }
}
