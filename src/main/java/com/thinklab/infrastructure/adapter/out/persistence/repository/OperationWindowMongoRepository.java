package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.OperationWindowEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Infrastructure Adapter: Reactive repository for {@link OperationWindowEntity} persistence.
 * Micronaut Data Mongo generates the AOT query implementations (validated at compile time).
 *
 * @author ThinkLab
 * @since 1.0
 */
@MongoRepository
public interface OperationWindowMongoRepository extends ReactorCrudRepository<OperationWindowEntity, UUID> {

    Flux<OperationWindowEntity> findByOrganisationId(UUID organisationId);

    Flux<OperationWindowEntity> findByOrganisationIdAndStatus(UUID organisationId, WindowStatus status);

    /**
     * Overlap candidates: {@code startAt < end AND endAt > start} for the given statuses. The method
     * name fixes the parameter order — the third argument bounds {@code startAt}, the fourth {@code endAt}.
     */
    Flux<OperationWindowEntity> findByOrganisationIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            UUID organisationId, Collection<WindowStatus> statuses, Instant end, Instant start);
}
