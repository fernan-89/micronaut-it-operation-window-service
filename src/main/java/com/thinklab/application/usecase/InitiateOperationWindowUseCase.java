package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.mapper.OperationWindowMapper;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.OperationWindowRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the scheduling of an Operation Window (BIAN Behavior Qualifier: {@code initiate}).
 *
 * <p>Flow: build a throw-away aggregate to validate every scheduling invariant (fails fast with 400,
 * before any I/O), prove the interval is collision-free (409 with the impact otherwise), obtain a
 * Sovereign ID, then persist.
 */
@Singleton
public class InitiateOperationWindowUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateOperationWindowUseCase.class);

    private final HashServicePort hashServicePort;
    private final OperationWindowRepository repository;
    private final WindowCollisionGuard collisionGuard;

    public InitiateOperationWindowUseCase(HashServicePort hashServicePort, OperationWindowRepository repository,
                                          WindowCollisionGuard collisionGuard) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
        this.collisionGuard = collisionGuard;
    }

    public Mono<OperationWindowResponse> execute(UUID organisationId, InitiateOperationWindowRequest request, String executor) {
        log.info("[USE CASE] Scheduling operation window for organisation: {} assets: {}", organisationId, request.targetAssetIds());

        // Validates every invariant up-front (throws IllegalArgumentException -> 400) without any I/O.
        OperationWindowMapper.toDomain(request, UUID.randomUUID(), organisationId, executor);

        return collisionGuard.assertFree(organisationId, null, request.targetAssetIds(), request.startAt(), request.endAt())
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("operation-window-creation")))
                .map(sovereignId -> OperationWindowMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(repository::create)
                .map(OperationWindowMapper::toResponse);
    }
}
