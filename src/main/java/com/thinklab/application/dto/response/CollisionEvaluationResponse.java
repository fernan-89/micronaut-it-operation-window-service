package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO projecting the result of a collision dry-run: whether the interval is free and, when it is
 * not, the full impact (every conflicting window and the assets in conflict).
 */
@Serdeable
public record CollisionEvaluationResponse(boolean collides, List<ConflictResponse> conflicts) {

    @Serdeable
    public record ConflictResponse(UUID windowId, String title, Instant startAt, Instant endAt,
                                   Set<UUID> overlappingAssetIds) {}
}
