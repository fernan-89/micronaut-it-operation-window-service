package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Entity: Persistence model for the OperationWindow aggregate mapped to MongoDB via
 * Micronaut Data (same strategy as {@link MaintenanceTicketEntity}, see ADR-016).
 *
 * <p>The index serves the collision query (ADR-018): equality on {@code organisationId}, {@code $in} on
 * {@code status}, then the {@code startAt} range, so it is scanned for exactly the tenant's active windows
 * that start before the requested end. The tenant listings use its prefix. The kit creates it at startup.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@MappedEntity("operation_windows")
@Indexes(@Index(columns = {"organisationId", "status", "startAt"}))
public record OperationWindowEntity(

        @Id
        UUID id,

        UUID organisationId,

        String title,

        String description,

        WindowType windowType,

        List<UUID> targetAssetIds,

        Instant startAt,

        Instant endAt,

        WindowStatus status,

        UUID maintenanceTicketId,

        String requestedBy,

        Instant createdAt,

        Instant updatedAt,

        @Version
        Long version
) {

    public OperationWindowEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: Window Entity ID cannot be null.");
        Objects.requireNonNull(organisationId, "Persistence Invariant Violation: Organisation ID cannot be null.");
        Objects.requireNonNull(title, "Persistence Invariant Violation: Title cannot be null.");
        Objects.requireNonNull(windowType, "Persistence Invariant Violation: Window Type cannot be null.");
        Objects.requireNonNull(startAt, "Persistence Invariant Violation: StartAt cannot be null.");
        Objects.requireNonNull(endAt, "Persistence Invariant Violation: EndAt cannot be null.");
        Objects.requireNonNull(status, "Persistence Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(createdAt, "Persistence Invariant Violation: CreatedAt timestamp cannot be null.");

        if (title.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Title cannot be blank.");
        }
        targetAssetIds = targetAssetIds != null ? List.copyOf(targetAssetIds) : List.of();
    }

    public static OperationWindowEntity fromDomain(OperationWindow domain) {
        Objects.requireNonNull(domain, "Infrastructure constraint violated: Domain aggregate cannot be null for entity mapping.");

        return new OperationWindowEntity(
                domain.getId(),
                domain.getOrganisationId(),
                domain.getTitle(),
                domain.getDescription(),
                domain.getWindowType(),
                new ArrayList<>(domain.getTargetAssetIds()),
                domain.getStartAt(),
                domain.getEndAt(),
                domain.getStatus(),
                domain.getMaintenanceTicketId(),
                domain.getRequestedBy(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                null
        );
    }

    public OperationWindow toDomain() {
        return OperationWindow.reconstitute(id, organisationId, title, description, windowType,
                new LinkedHashSet<>(targetAssetIds), startAt, endAt, status, maintenanceTicketId, requestedBy,
                createdAt, updatedAt);
    }

    /** Copy with a new schedule and a refreshed {@code updatedAt}; identity, status and version are preserved. */
    public OperationWindowEntity withSchedule(Instant newStartAt, Instant newEndAt) {
        return new OperationWindowEntity(id, organisationId, title, description, windowType, targetAssetIds,
                newStartAt, newEndAt, status, maintenanceTicketId, requestedBy, createdAt, Instant.now(), version);
    }

    /** Copy with a new status and a refreshed {@code updatedAt}; identity, schedule and version are preserved. */
    public OperationWindowEntity withStatus(WindowStatus newStatus) {
        return new OperationWindowEntity(id, organisationId, title, description, windowType, targetAssetIds,
                startAt, endAt, newStatus, maintenanceTicketId, requestedBy, createdAt, Instant.now(), version);
    }
}
