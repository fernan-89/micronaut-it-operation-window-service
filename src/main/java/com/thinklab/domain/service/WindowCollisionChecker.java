package com.thinklab.domain.service;

import com.thinklab.domain.model.OperationWindow;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domain Service implementing the collision and impact check of the scheduler (OPS-02).
 *
 * <p>Two windows collide when they (1) are both active ({@code SCHEDULED} or {@code IN_PROGRESS}),
 * (2) share at least one target asset and (3) overlap in time over the half-open interval
 * {@code [startAt, endAt)}. The result is the <b>impact</b>: which existing windows are hit and on
 * which assets, so a planner can act on it instead of just being told "no".
 *
 * <p>Pure and stateless: the persistence layer supplies a coarse pre-filtered candidate set
 * (tenant + active + time overlap) and this service applies the exact rules, so the business rule
 * lives in the domain and not in a database query.
 */
public final class WindowCollisionChecker {

    private WindowCollisionChecker() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Detects the conflicts a candidate interval would create against the existing windows.
     *
     * @param candidateWindowId the window being (re)scheduled, excluded from the comparison; may be {@code null}
     * @param assetIds          the assets the candidate targets
     * @param startAt           candidate start
     * @param endAt             candidate end
     * @param existing          windows to compare against
     * @return the conflicts ordered by start time (empty when the candidate is free to schedule)
     */
    public static List<WindowConflict> detect(UUID candidateWindowId, Set<UUID> assetIds, Instant startAt,
                                              Instant endAt, Collection<OperationWindow> existing) {
        return detect(candidateWindowId, assetIds, startAt, endAt, existing, false);
    }

    /**
     * Same as above; with {@code ignoreChangeFreeze} the {@code CHANGE_FREEZE} windows are not compared (a deliberate,
     * audited override requested by the caller) - every other active window still collides.
     */
    public static List<WindowConflict> detect(UUID candidateWindowId, Set<UUID> assetIds, Instant startAt,
                                              Instant endAt, Collection<OperationWindow> existing,
                                              boolean ignoreChangeFreeze) {
        OperationWindow.validateTargets(assetIds);
        OperationWindow.validateInterval(startAt, endAt);

        return existing.stream()
                .filter(window -> candidateWindowId == null || !window.getId().equals(candidateWindowId))
                .filter(OperationWindow::isActive)
                .filter(window -> !(ignoreChangeFreeze && window.getWindowType() == OperationWindow.WindowType.CHANGE_FREEZE))
                .filter(window -> window.overlapsInTime(startAt, endAt))
                .map(window -> new WindowConflict(window.getId(), window.getTitle(), window.getStartAt(),
                        window.getEndAt(), window.sharedAssetsWith(assetIds)))
                .filter(conflict -> !conflict.overlappingAssetIds().isEmpty())
                .sorted(Comparator.comparing(WindowConflict::startAt).thenComparing(WindowConflict::windowId))
                .toList();
    }

    /**
     * One colliding window and the assets that make it collide.
     */
    public record WindowConflict(UUID windowId, String title, Instant startAt, Instant endAt,
                                 Set<UUID> overlappingAssetIds) {}
}
