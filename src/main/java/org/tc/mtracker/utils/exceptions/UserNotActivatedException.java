package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotActivatedException extends ApiException {
    public UserNotActivatedException(String message) {
        super(HttpStatus.FORBIDDEN, "user_not_activated", message);
    }
}
