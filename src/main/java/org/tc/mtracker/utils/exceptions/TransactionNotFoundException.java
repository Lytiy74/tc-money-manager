package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends ApiException {
    public TransactionNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "transaction_not_found", message);
    }
}
