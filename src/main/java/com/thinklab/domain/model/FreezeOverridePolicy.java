package com.thinklab.domain.model;

import java.util.Set;

/**
 * Who may be granted a CHANGE_FREEZE override (ADR-020, ADR-021): with security on, only a caller whose verified role is {@code ADMIN}
 * (or {@code SERVICE}, another platform service acting for one); an OPERATOR, REQUESTER or VIEWER may schedule a window
 * normally but cannot waive a freeze. With security off there is no role at all ({@code null}) and nothing is enforced, the same
 * "off by default" posture as the rest of the platform.
 *
 * <p>This deliberately reuses the roles the kit already has. A dedicated change-manager role is the better long-term answer but
 * needs a kit release and a rollout to every service; until then ADMIN is the narrowest role that exists.
 */
public final class FreezeOverridePolicy {

    private static final Set<String> ELEVATED = Set.of("ADMIN", "SERVICE");

    private FreezeOverridePolicy() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** True when the verified role may waive a freeze; {@code null} means security is off, so nothing is enforced. */
    public static boolean permits(String verifiedRole) {
        return verifiedRole == null || ELEVATED.contains(verifiedRole);
    }
}
