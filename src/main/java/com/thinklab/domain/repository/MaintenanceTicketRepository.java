package com.thinklab.domain.repository;

import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for MaintenanceTicket persistence operations (IT Operation Window Service Domain).
 * Part of the pure Domain Layer.
 *
 * <p>There is no {@code deleteById} — every terminal transition is a status change to
 * {@link TicketStatus#COMPLETED}, never a physical deletion.
 */
public interface MaintenanceTicketRepository {

    Mono<MaintenanceTicket> create(MaintenanceTicket ticket);

    Mono<MaintenanceTicket> findById(UUID id);

    Flux<MaintenanceTicket> findAllByOrganisationId(UUID organisationId, TicketStatus status);

    Mono<Void> updateStatus(UUID id, TicketStatus status);

    /**
     * Persists both the appended comment and the (possibly unchanged) resulting status in a
     * single write, mirroring how sub-entities are appended in the Party Reference Data
     * Directory Service Domain.
     */
    Mono<Void> appendComment(UUID id, Comment comment, TicketStatus newStatus);
}
