package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApiException {
    public AccountNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "account_not_found", message);
    }
}
