package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class CategoryAlreadyExistsException extends ApiException {
    public CategoryAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "category_already_exists", message);
    }
}
