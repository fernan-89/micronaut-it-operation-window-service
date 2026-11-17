package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.MaintenanceTicketEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Infrastructure Adapter: Reactive repository for {@link MaintenanceTicketEntity} persistence.
 * Micronaut Data Mongo generates the AOT query implementation — no manual codec registration
 * required (see ADR-016; this deliberately avoids the raw-driver codec pitfall found in the
 * Party Reference Data Directory Service Domain).
 *
 * @author ThinkLab
 * @since 1.0
 */
@MongoRepository
public interface MaintenanceTicketMongoRepository extends ReactorCrudRepository<MaintenanceTicketEntity, UUID> {

    /** The entity only if it belongs to the organisation: another tenant's is the same as none (ADR-029 of the tenant audit). */
    Mono<MaintenanceTicketEntity> findByIdAndOrganisationId(UUID id, UUID organisationId);

    Flux<MaintenanceTicketEntity> findByOrganisationId(UUID organisationId);

    Flux<MaintenanceTicketEntity> findByOrganisationIdAndStatus(UUID organisationId, TicketStatus status);
}
