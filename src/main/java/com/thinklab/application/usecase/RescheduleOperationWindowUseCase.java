package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.RescheduleOperationWindowRequest;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.repository.OperationWindowRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates moving a SCHEDULED window (BIAN Behavior Qualifier: {@code reschedule}).
 *
 * <p>The aggregate validates the move (only SCHEDULED, valid interval, not in the past) and the
 * collision guard proves the new interval is free — excluding the window itself — before the
 * granular schedule update is issued.
 */
@Singleton
public class RescheduleOperationWindowUseCase {

    private static final Logger log = LoggerFactory.getLogger(RescheduleOperationWindowUseCase.class);

    private final OperationWindowRepository repository;
    private final WindowCollisionGuard collisionGuard;

    public RescheduleOperationWindowUseCase(OperationWindowRepository repository, WindowCollisionGuard collisionGuard) {
        this.repository = repository;
        this.collisionGuard = collisionGuard;
    }

    public Mono<Void> execute(UUID id, RescheduleOperationWindowRequest request) {
        log.info("[USE CASE] Rescheduling operation window ID: {}", id);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new WindowNotFoundException(id)))
                .flatMap(window -> {
                    window.reschedule(request.startAt(), request.endAt());
                    return collisionGuard
                            .assertFree(window.getOrganisationId(), window.getId(), window.getTargetAssetIds(),
                                    window.getStartAt(), window.getEndAt())
                            .then(Mono.defer(() -> repository.updateSchedule(id, window.getStartAt(), window.getEndAt())));
                });
    }
}
