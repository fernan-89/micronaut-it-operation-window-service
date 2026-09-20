package com.thinklab.domain.exception;

import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;

import java.util.List;

/**
 * Domain Exception: the requested interval collides with one or more active windows that target
 * the same assets (OPS-02).
 *
 * <p>Carries the full <b>impact</b> — every conflicting window and the assets in conflict — so the
 * RFC 7807 problem document can expose it to the planner as the {@code conflicts} extension member.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict, {@code error_code} {@code ERR-COL-00409}.
 */
public class WindowCollisionException extends BusinessException {

    private static final String ERROR_CODE = "ERR-COL-00409";

    private final transient List<WindowConflict> conflicts;

    public WindowCollisionException(List<WindowConflict> conflicts) {
        super(ERROR_CODE, String.format(
                "The requested window collides with %d active window(s) on the same assets.", conflicts.size()));
        this.conflicts = List.copyOf(conflicts);
    }

    public List<WindowConflict> getConflicts() {
        return conflicts;
    }
}
