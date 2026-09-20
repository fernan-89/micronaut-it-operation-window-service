package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for a collision dry-run (BIAN Behavior Qualifier: {@code collision-check/evaluate}). Nothing
 * is persisted. {@code excludeWindowId} lets a planner ask "would moving window X here collide?".
 */
@Serdeable
public record EvaluateWindowCollisionRequest(

        @NotEmpty(message = "At least one target asset is required")
        Set<UUID> targetAssetIds,

        @NotNull(message = "Start instant is required")
        Instant startAt,

        @NotNull(message = "End instant is required")
        Instant endAt,

        UUID excludeWindowId
) {}
