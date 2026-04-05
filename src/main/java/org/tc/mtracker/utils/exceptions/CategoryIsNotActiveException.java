package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class CategoryIsNotActiveException extends ApiException {
    public CategoryIsNotActiveException(String message) {
        super(HttpStatus.BAD_REQUEST, "category_inactive", message);
    }
}
