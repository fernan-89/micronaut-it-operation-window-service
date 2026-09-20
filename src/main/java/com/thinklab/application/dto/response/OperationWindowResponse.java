package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for Operation Window output payload. Enforces the DTO Isolation Pattern by preventing the
 * pure Domain Model from bleeding out to the HTTP boundary.
 */
@Serdeable
public record OperationWindowResponse(
        UUID id,
        UUID organisationId,
        String title,
        String description,
        String windowType,
        Set<UUID> targetAssetIds,
        Instant startAt,
        Instant endAt,
        String status,
        UUID maintenanceTicketId,
        String requestedBy,
        Instant createdAt,
        Instant updatedAt
) {}
