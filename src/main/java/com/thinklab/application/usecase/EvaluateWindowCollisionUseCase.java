package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.EvaluateWindowCollisionRequest;
import com.thinklab.application.dto.response.CollisionEvaluationResponse;
import com.thinklab.application.mapper.OperationWindowMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Collision dry-run (BIAN Behavior Qualifier: {@code collision-check/evaluate}). Answers "would this
 * interval collide, and with what?" without persisting anything, so a planner can iterate on a
 * slot before committing to it.
 */
@Singleton
public class EvaluateWindowCollisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateWindowCollisionUseCase.class);

    private final WindowCollisionGuard collisionGuard;

    public EvaluateWindowCollisionUseCase(WindowCollisionGuard collisionGuard) {
        this.collisionGuard = collisionGuard;
    }

    public Mono<CollisionEvaluationResponse> execute(UUID organisationId, EvaluateWindowCollisionRequest request) {
        log.info("[USE CASE] Evaluating collision for organisation: {} assets: {}", organisationId, request.targetAssetIds());

        return Mono.defer(() -> collisionGuard.detect(organisationId, request.excludeWindowId(),
                        request.targetAssetIds(), request.startAt(), request.endAt()))
                .map(OperationWindowMapper::toEvaluation);
    }
}
