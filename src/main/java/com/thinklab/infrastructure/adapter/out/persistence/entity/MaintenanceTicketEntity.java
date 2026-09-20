package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure Entity: Persistence model for the MaintenanceTicket aggregate mapped to MongoDB
 * via Micronaut Data (not the raw reactive driver — see ADR-016 for why).
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@MappedEntity("maintenance_tickets")
public record MaintenanceTicketEntity(

        @Id
        UUID id,

        UUID organisationId,

        UUID assetId,

        String title,

        String description,

        TicketStatus status,

        List<CommentEntity> comments,

        Instant createdAt,

        Instant updatedAt,

        @Version
        Long version
) {

    public MaintenanceTicketEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: Ticket Entity ID cannot be null.");
        Objects.requireNonNull(organisationId, "Persistence Invariant Violation: Organisation ID cannot be null.");
        Objects.requireNonNull(assetId, "Persistence Invariant Violation: Asset ID cannot be null.");
        Objects.requireNonNull(title, "Persistence Invariant Violation: Title cannot be null.");
        Objects.requireNonNull(status, "Persistence Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(createdAt, "Persistence Invariant Violation: CreatedAt timestamp cannot be null.");

        if (title.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Title cannot be blank.");
        }
    }

    public static MaintenanceTicketEntity fromDomain(MaintenanceTicket domain) {
        Objects.requireNonNull(domain, "Infrastructure constraint violated: Domain aggregate cannot be null for entity mapping.");

        return new MaintenanceTicketEntity(
                domain.getId(),
                domain.getOrganisationId(),
                domain.getAssetId(),
                domain.getTitle(),
                domain.getDescription(),
                domain.getStatus(),
                domain.getComments().stream()
                        .map(c -> new CommentEntity(c.commentId(), c.author(), c.text(), c.createdAt()))
                        .collect(Collectors.toList()),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                null
        );
    }

    public MaintenanceTicket toDomain() {
        List<Comment> domainComments = comments != null
                ? comments.stream().map(c -> new Comment(c.commentId(), c.author(), c.text(), c.createdAt())).collect(Collectors.toList())
                : new ArrayList<>();

        return MaintenanceTicket.reconstitute(
                id, organisationId, assetId, title, description, status, domainComments, createdAt, updatedAt
        );
    }

    @Serdeable
    @Introspected
    public record CommentEntity(UUID commentId, String author, String text, Instant createdAt) {}
}
