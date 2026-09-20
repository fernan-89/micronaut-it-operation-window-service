package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal or unpermitted lifecycle state transition attempt
 * on a {@link com.thinklab.domain.model.MaintenanceTicket}.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 *
 * @author ThinkLab
 * @since 1.0
 */
public class InvalidTicketStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-OPS-00409";

    public InvalidTicketStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
