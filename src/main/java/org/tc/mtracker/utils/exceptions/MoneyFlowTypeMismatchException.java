package org.tc.mtracker.utils.exceptions;

import org.springframework.http.HttpStatus;

public class MoneyFlowTypeMismatchException extends ApiException {
    public MoneyFlowTypeMismatchException(String message) {
        super(HttpStatus.BAD_REQUEST, "money_flow_type_mismatch", message);
    }
}
