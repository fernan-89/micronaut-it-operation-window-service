package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.repository.OperationWindowRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.OperationWindowEntity;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Micronaut Data Mongo Repository Adapter.
 * Implements the pure Domain Port using the AOT-generated {@link OperationWindowMongoRepository}.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Singleton
public class OperationWindowRepositoryAdapter implements OperationWindowRepository {

    private static final Logger log = LoggerFactory.getLogger(OperationWindowRepositoryAdapter.class);

    private static final List<WindowStatus> ACTIVE_STATUSES = List.of(WindowStatus.SCHEDULED, WindowStatus.IN_PROGRESS);

    private final OperationWindowMongoRepository repository;

    public OperationWindowRepositoryAdapter(OperationWindowMongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Infrastructure constraint violated: OperationWindowMongoRepository cannot be null.");
    }

    @Override
    public Mono<OperationWindow> create(OperationWindow window) {
        log.debug("[PERSISTENCE] Creating OperationWindow Aggregate: {}", window.getId());

        return repository.save(OperationWindowEntity.fromDomain(window))
                .map(OperationWindowEntity::toDomain);
    }

    @Override
    public Mono<OperationWindow> findById(UUID id) {
        log.debug("[PERSISTENCE] Fetching OperationWindow Aggregate by ID: {}", id);

        return repository.findById(id).map(OperationWindowEntity::toDomain);
    }

    @Override
    public Flux<OperationWindow> findAllByOrganisationId(UUID organisationId, WindowStatus status, UUID assetId) {
        log.debug("[PERSISTENCE] Listing OperationWindows for Organisation: {} [status={}] [asset={}]", organisationId, status, assetId);

        Flux<OperationWindowEntity> source = status != null
                ? repository.findByOrganisationIdAndStatus(organisationId, status)
                : repository.findByOrganisationId(organisationId);

        if (assetId != null) {
            source = source.filter(entity -> entity.targetAssetIds().contains(assetId));
        }

        return source.map(OperationWindowEntity::toDomain);
    }

    @Override
    public Flux<OperationWindow> findActiveOverlapping(UUID organisationId, Instant startAt, Instant endAt) {
        log.debug("[PERSISTENCE] Fetching active windows of Organisation {} overlapping [{}, {})", organisationId, startAt, endAt);

        return repository
                .findByOrganisationIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(organisationId, ACTIVE_STATUSES, endAt, startAt)
                .map(OperationWindowEntity::toDomain);
    }

    @Override
    public Mono<Void> updateSchedule(UUID id, Instant startAt, Instant endAt) {
        log.debug("[PERSISTENCE] Updating schedule of OperationWindow {} to [{}, {})", id, startAt, endAt);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new WindowNotFoundException(id)))
                .flatMap(entity -> repository.update(entity.withSchedule(startAt, endAt)))
                .then();
    }

    @Override
    public Mono<Void> updateStatus(UUID id, WindowStatus status) {
        log.debug("[PERSISTENCE] Updating status of OperationWindow {} to {}", id, status);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new WindowNotFoundException(id)))
                .flatMap(entity -> repository.update(entity.withStatus(status)))
                .then();
    }
}
