package com.thinklab.application.usecase;

import com.thinklab.domain.exception.WindowCollisionException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.repository.OperationWindowRepository;
import com.thinklab.domain.service.WindowCollisionChecker;
import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service shared by every use case that has to prove an interval is free (OPS-02):
 * it loads the coarse candidate set from the port and lets the domain service apply the exact rules.
 *
 * <p>Known limitation: check-then-write is not atomic across concurrent requests. Two planners
 * submitting colliding windows in the same instant can both pass the check; the platform accepts
 * this for v1 (documented in ADR-018) and the {@code collision-check/evaluate} dry-run plus the
 * ordered {@code conflicts} list make the residual case detectable and correctable.
 */
@Singleton
public class WindowCollisionGuard {

    private final OperationWindowRepository repository;

    public WindowCollisionGuard(OperationWindowRepository repository) {
        this.repository = repository;
    }

    /** Emits the conflicts (possibly empty) the given interval would create. */
    public Mono<List<WindowConflict>> detect(UUID organisationId, UUID excludeWindowId, Set<UUID> assetIds,
                                             Instant startAt, Instant endAt) {
        return detect(organisationId, excludeWindowId, assetIds, startAt, endAt, false);
    }

    /** As above; {@code ignoreChangeFreeze} skips the CHANGE_FREEZE windows (audited override). */
    public Mono<List<WindowConflict>> detect(UUID organisationId, UUID excludeWindowId, Set<UUID> assetIds,
                                             Instant startAt, Instant endAt, boolean ignoreChangeFreeze) {
        OperationWindow.validateTargets(assetIds);
        OperationWindow.validateInterval(startAt, endAt);

        return repository.findActiveOverlapping(organisationId, startAt, endAt)
                .collectList()
                .map(candidates -> WindowCollisionChecker.detect(excludeWindowId, assetIds, startAt, endAt, candidates, ignoreChangeFreeze));
    }

    /** Completes when the interval is free, or errors with a {@link WindowCollisionException}. */
    public Mono<Void> assertFree(UUID organisationId, UUID excludeWindowId, Set<UUID> assetIds,
                                 Instant startAt, Instant endAt) {
        return assertFree(organisationId, excludeWindowId, assetIds, startAt, endAt, false);
    }

    /** As above; {@code ignoreChangeFreeze} skips the CHANGE_FREEZE windows (audited override). */
    public Mono<Void> assertFree(UUID organisationId, UUID excludeWindowId, Set<UUID> assetIds,
                                 Instant startAt, Instant endAt, boolean ignoreChangeFreeze) {
        return detect(organisationId, excludeWindowId, assetIds, startAt, endAt, ignoreChangeFreeze)
                .flatMap(conflicts -> conflicts.isEmpty()
                        ? Mono.<Void>empty()
                        : Mono.error(new WindowCollisionException(conflicts)));
    }
}
