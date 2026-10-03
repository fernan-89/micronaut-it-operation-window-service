package com.thinklab.domain.exception;

/**
 * Domain Exception: the caller's role may not be granted a CHANGE_FREEZE override (ADR-021). The window can still be scheduled
 * without the override.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class FreezeOverrideNotPermittedException extends BusinessException {

    public FreezeOverrideNotPermittedException(String role) {
        super("ERR-WIN-00403", "The role " + role + " may not override a CHANGE_FREEZE: that needs ADMIN or SERVICE. The window can still be scheduled without the override.");
    }
}
