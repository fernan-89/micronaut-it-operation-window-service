package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.mapper.OperationWindowMapper;
import com.thinklab.domain.exception.FreezeOverrideNotPermittedException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.FreezeOverridePolicy;
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

    /** Without a verified role (security off, or a caller that does not ask for an override). */
    public Mono<OperationWindowResponse> execute(UUID organisationId, InitiateOperationWindowRequest request, String executor) {
        return execute(organisationId, request, executor, null);
    }

    /**
     * @param role the verified role (X-Role) of the caller, {@code null} when security is off; decides whether a freeze override
     *             may be granted (ADR-021)
     */
    public Mono<OperationWindowResponse> execute(UUID organisationId, InitiateOperationWindowRequest request, String executor, String role) {
        String justification = request.changeFreezeOverrideJustification();
        boolean overrideFreeze = justification != null;
        if (overrideFreeze && !FreezeOverridePolicy.permits(role)) {
            return Mono.error(new FreezeOverrideNotPermittedException(role));
        }
        if (overrideFreeze && (justification.isBlank() || request.windowType() != OperationWindow.WindowType.DEPLOYMENT)) {
            throw new IllegalArgumentException("A change-freeze override needs a non-blank justification and is only allowed for a DEPLOYMENT window.");
        }
        log.info("[USE CASE] Scheduling operation window for organisation: {} assets: {}", organisationId, request.targetAssetIds());

        // Validates every invariant up-front (throws IllegalArgumentException -> 400) without any I/O.
        OperationWindowMapper.toDomain(request, UUID.randomUUID(), organisationId, executor);

        // The override leaves a trace on the window record itself (its description) as well as in the log.
        InitiateOperationWindowRequest effective = overrideFreeze ? withOverrideNote(request, justification) : request;
        if (overrideFreeze) {
            log.warn("[CHANGE_FREEZE OVERRIDE] organisation: {} executor: {} assets: {} justification: {}",
                    organisationId, executor, request.targetAssetIds(), justification);
        }

        return collisionGuard.assertFree(organisationId, null, request.targetAssetIds(), request.startAt(), request.endAt(), overrideFreeze)
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("operation-window-creation")))
                .map(sovereignId -> OperationWindowMapper.toDomain(effective, sovereignId, organisationId, executor))
                .flatMap(repository::create)
                .map(OperationWindowMapper::toResponse);
    }

    private static InitiateOperationWindowRequest withOverrideNote(InitiateOperationWindowRequest request, String justification) {
        String note = "[CHANGE_FREEZE OVERRIDE] " + justification;
        String description = request.description() == null || request.description().isBlank() ? note : request.description() + '\n' + note;
        return new InitiateOperationWindowRequest(request.title(), description, request.windowType(), request.targetAssetIds(),
                request.startAt(), request.endAt(), request.maintenanceTicketId(), justification);
    }
}
