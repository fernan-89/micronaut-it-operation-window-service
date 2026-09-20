package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.MaintenanceTicketEntity;
import com.thinklab.infrastructure.adapter.out.persistence.entity.MaintenanceTicketEntity.CommentEntity;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Micronaut Data Mongo Repository Adapter.
 * Implements the pure Domain Port using the AOT-generated {@link MaintenanceTicketMongoRepository}.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Singleton
public class MaintenanceTicketRepositoryAdapter implements MaintenanceTicketRepository {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTicketRepositoryAdapter.class);

    private final MaintenanceTicketMongoRepository repository;

    public MaintenanceTicketRepositoryAdapter(MaintenanceTicketMongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Infrastructure constraint violated: MaintenanceTicketMongoRepository cannot be null.");
    }

    @Override
    public Mono<MaintenanceTicket> create(MaintenanceTicket ticket) {
        log.debug("[PERSISTENCE] Creating MaintenanceTicket Aggregate: {}", ticket.getId());

        return repository.save(MaintenanceTicketEntity.fromDomain(ticket))
                .map(MaintenanceTicketEntity::toDomain);
    }

    @Override
    public Mono<MaintenanceTicket> findById(UUID id) {
        log.debug("[PERSISTENCE] Fetching MaintenanceTicket Aggregate by ID: {}", id);

        return repository.findById(id).map(MaintenanceTicketEntity::toDomain);
    }

    @Override
    public Flux<MaintenanceTicket> findAllByOrganisationId(UUID organisationId, TicketStatus status) {
        log.debug("[PERSISTENCE] Listing MaintenanceTickets for Organisation: {} [status={}]", organisationId, status);

        Flux<MaintenanceTicketEntity> source = status != null
                ? repository.findByOrganisationIdAndStatus(organisationId, status)
                : repository.findByOrganisationId(organisationId);

        return source.map(MaintenanceTicketEntity::toDomain);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, TicketStatus status) {
        log.debug("[PERSISTENCE] Updating status of MaintenanceTicket {} to {}", id, status);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new MaintenanceTicketNotFoundException(id)))
                .flatMap(entity -> {
                    MaintenanceTicketEntity updated = new MaintenanceTicketEntity(
                            entity.id(), entity.organisationId(), entity.assetId(), entity.title(), entity.description(),
                            status, entity.comments(), entity.createdAt(), Instant.now(), entity.version()
                    );
                    return repository.update(updated);
                })
                .then();
    }

    @Override
    public Mono<Void> appendComment(UUID id, Comment comment, TicketStatus newStatus) {
        log.debug("[PERSISTENCE] Appending comment to MaintenanceTicket {} [newStatus={}]", id, newStatus);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new MaintenanceTicketNotFoundException(id)))
                .flatMap(entity -> {
                    List<CommentEntity> comments = entity.comments() != null ? new ArrayList<>(entity.comments()) : new ArrayList<>();
                    comments.add(new CommentEntity(comment.commentId(), comment.author(), comment.text(), comment.createdAt()));

                    MaintenanceTicketEntity updated = new MaintenanceTicketEntity(
                            entity.id(), entity.organisationId(), entity.assetId(), entity.title(), entity.description(),
                            newStatus, comments, entity.createdAt(), Instant.now(), entity.version()
                    );
                    return repository.update(updated);
                })
                .then();
    }
}
