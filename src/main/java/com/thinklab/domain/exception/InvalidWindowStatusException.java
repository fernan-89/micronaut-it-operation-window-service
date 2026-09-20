package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal or unpermitted lifecycle operation on an
 * {@link com.thinklab.domain.model.OperationWindow} (illegal or idempotent state transition, or
 * rescheduling a window that is no longer SCHEDULED).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class InvalidWindowStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-WIN-00409";

    public InvalidWindowStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
