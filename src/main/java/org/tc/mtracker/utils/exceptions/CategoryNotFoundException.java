package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {
    public CategoryNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "category_not_found", message);
    }
}
