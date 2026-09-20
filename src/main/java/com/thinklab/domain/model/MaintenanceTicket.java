package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidTicketStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Core Domain Model representing the MaintenanceTicket Aggregate Root.
 *
 * <p><b>BIAN Alignment:</b> This is the Control Record of the {@code it-operation-window} Service
 * Domain — a maintenance ticket linked to an asset from the IT Asset Registry Service Domain
 * (referenced only by an opaque {@code assetId}, no synchronous cross-service validation in v1).
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers.
 */
public class MaintenanceTicket {

    private final UUID id;
    private final UUID organisationId;
    private final UUID assetId;
    private String title;
    private String description;
    private TicketStatus status;
    private final List<Comment> comments;
    private final Instant createdAt;
    private Instant updatedAt;

    private MaintenanceTicket(UUID id, UUID organisationId, UUID assetId, String title, String description) {
        this.id = id;
        this.organisationId = organisationId;
        this.assetId = assetId;
        this.title = title;
        this.description = description;
        this.status = TicketStatus.OPEN;
        this.comments = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private MaintenanceTicket(
            UUID id,
            UUID organisationId,
            UUID assetId,
            String title,
            String description,
            TicketStatus status,
            List<Comment> comments,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.organisationId = organisationId;
        this.assetId = assetId;
        this.title = title;
        this.description = description;
        this.status = status != null ? status : TicketStatus.OPEN;
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Static factory method for aggregate creation (BIAN Behavior Qualifier: {@code initiate}).
     * The UUID must be provided by the orchestration layer after calling the Hash Token Registry.
     */
    public static MaintenanceTicket createNew(UUID id, UUID organisationId, UUID assetId, String title, String description) {
        if (id == null || organisationId == null || assetId == null || title == null || title.isBlank()) {
            throw new IllegalArgumentException("ID, Organisation ID, Asset ID, and Title are mandatory for MaintenanceTicket creation.");
        }
        return new MaintenanceTicket(id, organisationId, assetId, title, description);
    }

    /**
     * Reconstitutes an existing MaintenanceTicket aggregate from persistence layer.
     */
    public static MaintenanceTicket reconstitute(
            UUID id,
            UUID organisationId,
            UUID assetId,
            String title,
            String description,
            TicketStatus status,
            List<Comment> comments,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null || organisationId == null || assetId == null || title == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Asset ID, and Title are mandatory to reconstitute a MaintenanceTicket.");
        }
        return new MaintenanceTicket(id, organisationId, assetId, title, description, status, comments, createdAt, updatedAt);
    }

    // --- Domain Behaviors (State Mutations) ---

    /**
     * Behavior Qualifier: {@code control}. Transitions the ticket to the given target status,
     * enforcing the {@link TicketStatus} state machine.
     */
    public void changeStatus(TicketStatus newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null.");
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Appends a forensic comment to the ticket. Per the platform blueprint, a comment can
     * optionally drive a status transition — when {@code targetStatus} is present, it is applied
     * through {@link #changeStatus(TicketStatus)} (and therefore still validated by the FSM),
     * never as a bypassed raw field write.
     */
    public void addComment(Comment comment, Optional<TicketStatus> targetStatus) {
        if (comment == null) {
            throw new IllegalArgumentException("Comment cannot be null.");
        }
        this.comments.add(comment);
        this.updatedAt = Instant.now();
        targetStatus.ifPresent(this::changeStatus);
    }

    // --- Getters (Returning Unmodifiable Collections for Encapsulation) ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getAssetId() { return assetId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public List<Comment> getComments() { return Collections.unmodifiableList(comments); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // --- Nested Value Objects & Entities ---

    /**
     * Formal lifecycle state machine for the MaintenanceTicket Control Record, mirroring the
     * {@code HashStatus}/{@code OrganisationStatus} pattern used elsewhere in the platform.
     */
    public enum TicketStatus {
        OPEN, IN_ANALYSIS, AWAITING_PARTS, COMPLETED;

        /**
         * Validates if the transition from the current state to the target state is legally permitted.
         *
         * @throws InvalidTicketStatusException if the transition violates business compliance rules
         *                                       or is unnecessarily idempotent.
         */
        public void validateTransitionTo(TicketStatus targetStatus) {
            Objects.requireNonNull(targetStatus, "Target TicketStatus must not be null for transition validation.");

            if (this == targetStatus) {
                throw new InvalidTicketStatusException(String.format(
                        "Idempotency Violation: The MaintenanceTicket is already in the [%s] state.", this));
            }
            if (!canTransitionTo(targetStatus)) {
                throw new InvalidTicketStatusException(String.format(
                        "Compliance Violation: Illegal state transition from [%s] to [%s].", this, targetStatus));
            }
        }

        public boolean canTransitionTo(TicketStatus targetStatus) {
            if (targetStatus == null) {
                return false;
            }
            return switch (this) {
                case OPEN -> targetStatus == IN_ANALYSIS;
                case IN_ANALYSIS -> targetStatus == AWAITING_PARTS || targetStatus == COMPLETED;
                case AWAITING_PARTS -> targetStatus == IN_ANALYSIS;
                case COMPLETED -> false;
            };
        }
    }

    public record Comment(
            UUID commentId,
            String author,
            String text,
            Instant createdAt
    ) {}
}
