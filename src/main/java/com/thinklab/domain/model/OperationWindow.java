package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidWindowStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the OperationWindow Aggregate Root.
 *
 * <p><b>BIAN Alignment (ADR-013):</b> This is the Control Record of the {@code it-operation-window}
 * Service Domain — a bounded time interval during which one or more IT assets (referenced by opaque
 * {@code assetId}s from the IT Asset Registry) are taken under a planned operation such as
 * maintenance, patching, migration or a change freeze.
 *
 * <p><b>Scheduling invariants (OPS-01):</b> an interval must be positive, at least
 * {@link #MIN_DURATION} and at most {@link #MAX_DURATION}, must not start in the past, and must
 * target between 1 and {@link #MAX_TARGET_ASSETS} assets. Collision detection (OPS-02) lives in
 * {@link com.thinklab.domain.service.WindowCollisionChecker}; this aggregate exposes the primitives
 * ({@link #isActive()}, {@link #overlapsInTime}, {@link #sharedAssetsWith}) it is built on.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class OperationWindow {

    public static final Duration MIN_DURATION = Duration.ofMinutes(5);
    public static final Duration MAX_DURATION = Duration.ofHours(72);
    public static final int MAX_TARGET_ASSETS = 50;
    static final Duration PAST_TOLERANCE = Duration.ofMinutes(1);

    private final UUID id;
    private final UUID organisationId;
    private final String title;
    private final String description;
    private final WindowType windowType;
    private final Set<UUID> targetAssetIds;
    private Instant startAt;
    private Instant endAt;
    private WindowStatus status;
    private final UUID maintenanceTicketId;
    private final String requestedBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private OperationWindow(UUID id, UUID organisationId, String title, String description, WindowType windowType,
                            Set<UUID> targetAssetIds, Instant startAt, Instant endAt, UUID maintenanceTicketId,
                            String requestedBy) {
        this.id = id;
        this.organisationId = organisationId;
        this.title = title;
        this.description = description;
        this.windowType = windowType;
        this.targetAssetIds = new LinkedHashSet<>(targetAssetIds);
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = WindowStatus.SCHEDULED;
        this.maintenanceTicketId = maintenanceTicketId;
        this.requestedBy = requestedBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private OperationWindow(UUID id, UUID organisationId, String title, String description, WindowType windowType,
                            Set<UUID> targetAssetIds, Instant startAt, Instant endAt, WindowStatus status,
                            UUID maintenanceTicketId, String requestedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.title = title;
        this.description = description;
        this.windowType = windowType;
        this.targetAssetIds = targetAssetIds != null ? new LinkedHashSet<>(targetAssetIds) : new LinkedHashSet<>();
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status != null ? status : WindowStatus.SCHEDULED;
        this.maintenanceTicketId = maintenanceTicketId;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Static factory for aggregate creation (BIAN Behavior Qualifier: {@code initiate}). The UUID must
     * be provided by the orchestration layer after calling the Hash Token Registry.
     */
    public static OperationWindow createNew(UUID id, UUID organisationId, String title, String description,
                                            WindowType windowType, Set<UUID> targetAssetIds, Instant startAt,
                                            Instant endAt, UUID maintenanceTicketId, String requestedBy) {
        if (id == null || organisationId == null || windowType == null) {
            throw new IllegalArgumentException("ID, Organisation ID, and Window Type are mandatory for OperationWindow creation.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is mandatory for OperationWindow creation.");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable OperationWindow creation.");
        }
        validateTargets(targetAssetIds);
        validateInterval(startAt, endAt);
        requireNotInPast(startAt);
        return new OperationWindow(id, organisationId, title, description, windowType, targetAssetIds, startAt, endAt,
                maintenanceTicketId, requestedBy);
    }

    /**
     * Reconstitutes an existing OperationWindow aggregate from the persistence layer.
     */
    public static OperationWindow reconstitute(UUID id, UUID organisationId, String title, String description,
                                               WindowType windowType, Set<UUID> targetAssetIds, Instant startAt,
                                               Instant endAt, WindowStatus status, UUID maintenanceTicketId,
                                               String requestedBy, Instant createdAt, Instant updatedAt) {
        if (id == null || organisationId == null || title == null || windowType == null || startAt == null || endAt == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Title, Window Type, and the interval are mandatory to reconstitute an OperationWindow.");
        }
        return new OperationWindow(id, organisationId, title, description, windowType, targetAssetIds, startAt, endAt,
                status, maintenanceTicketId, requestedBy, createdAt, updatedAt);
    }

    // --- Validation helpers (also reused by the collision pre-check use case) ---

    public static void validateTargets(Set<UUID> targetAssetIds) {
        if (targetAssetIds == null || targetAssetIds.isEmpty()) {
            throw new IllegalArgumentException("At least one target asset is required.");
        }
        if (targetAssetIds.size() > MAX_TARGET_ASSETS) {
            throw new IllegalArgumentException("A window may target at most " + MAX_TARGET_ASSETS + " assets.");
        }
        if (targetAssetIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Target asset IDs cannot be null.");
        }
    }

    public static void validateInterval(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("Start and end instants are mandatory.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("The window must end after it starts.");
        }
        Duration duration = Duration.between(startAt, endAt);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new IllegalArgumentException("The window must last at least " + MIN_DURATION.toMinutes() + " minutes.");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("The window must not exceed " + MAX_DURATION.toHours() + " hours.");
        }
    }

    private static void requireNotInPast(Instant startAt) {
        if (startAt.isBefore(Instant.now().minus(PAST_TOLERANCE))) {
            throw new IllegalArgumentException("The window cannot start in the past.");
        }
    }

    // --- Domain Behaviors (State Mutations) ---

    /**
     * Behavior Qualifier: {@code reschedule}. Only a still-SCHEDULED window can move; an operation
     * already in progress or closed keeps the interval it was executed in.
     */
    public void reschedule(Instant newStartAt, Instant newEndAt) {
        if (this.status != WindowStatus.SCHEDULED) {
            throw new InvalidWindowStatusException(String.format(
                    "Compliance Violation: only a SCHEDULED window can be rescheduled; this one is [%s].", this.status));
        }
        validateInterval(newStartAt, newEndAt);
        requireNotInPast(newStartAt);
        this.startAt = newStartAt;
        this.endAt = newEndAt;
        this.updatedAt = Instant.now();
    }

    /**
     * Behavior Qualifier: {@code control}. Transitions the window to the given target status,
     * enforcing the {@link WindowStatus} state machine.
     */
    public void changeStatus(WindowStatus newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null.");
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void start() { changeStatus(WindowStatus.IN_PROGRESS); }

    public void complete() { changeStatus(WindowStatus.COMPLETED); }

    /** Terminal, soft — a cancelled window is kept for the record, never deleted. */
    public void cancel() { changeStatus(WindowStatus.CANCELLED); }

    // --- Collision primitives (OPS-02) ---

    /** A window blocks its assets only while it is SCHEDULED or IN_PROGRESS. */
    public boolean isActive() {
        return status == WindowStatus.SCHEDULED || status == WindowStatus.IN_PROGRESS;
    }

    /** Half-open interval overlap: {@code [startAt, endAt)}; back-to-back windows do not collide. */
    public boolean overlapsInTime(Instant otherStart, Instant otherEnd) {
        return this.startAt.isBefore(otherEnd) && otherStart.isBefore(this.endAt);
    }

    public Set<UUID> sharedAssetsWith(Set<UUID> otherAssetIds) {
        Set<UUID> shared = new LinkedHashSet<>(this.targetAssetIds);
        shared.retainAll(otherAssetIds);
        return shared;
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public WindowType getWindowType() { return windowType; }
    public Set<UUID> getTargetAssetIds() { return Collections.unmodifiableSet(targetAssetIds); }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public WindowStatus getStatus() { return status; }
    public UUID getMaintenanceTicketId() { return maintenanceTicketId; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // --- Nested Value Objects ---

    public enum WindowType {
        MAINTENANCE, PATCHING, DEPLOYMENT, MIGRATION, CHANGE_FREEZE
    }

    /**
     * Formal lifecycle state machine for the OperationWindow Control Record, mirroring the
     * {@code HashStatus}/{@code TicketStatus} pattern (ADR-013).
     *
     * <pre>
     * SCHEDULED -> IN_PROGRESS -> COMPLETED (terminal)
     * SCHEDULED | IN_PROGRESS -> CANCELLED (terminal)
     * </pre>
     */
    public enum WindowStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED;

        public void validateTransitionTo(WindowStatus targetStatus) {
            Objects.requireNonNull(targetStatus, "Target WindowStatus must not be null for transition validation.");

            if (this == targetStatus) {
                throw new InvalidWindowStatusException(String.format(
                        "Idempotency Violation: The OperationWindow is already in the [%s] state.", this));
            }
            if (!canTransitionTo(targetStatus)) {
                throw new InvalidWindowStatusException(String.format(
                        "Compliance Violation: Illegal state transition from [%s] to [%s].", this, targetStatus));
            }
        }

        public boolean canTransitionTo(WindowStatus targetStatus) {
            if (targetStatus == null) {
                return false;
            }
            return switch (this) {
                case SCHEDULED -> targetStatus == IN_PROGRESS || targetStatus == CANCELLED;
                case IN_PROGRESS -> targetStatus == COMPLETED || targetStatus == CANCELLED;
                case COMPLETED, CANCELLED -> false;
            };
        }
    }
}
