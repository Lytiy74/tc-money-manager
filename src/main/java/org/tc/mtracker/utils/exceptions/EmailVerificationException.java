package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class EmailVerificationException extends ApiException {
    public EmailVerificationException(String message) {
        super(HttpStatus.BAD_REQUEST, "email_verification_failed", message);
    }
}
