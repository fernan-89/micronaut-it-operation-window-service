package com.thinklab.domain.repository;

import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Port for OperationWindow persistence operations (IT Operation Window Service Domain).
 * Part of the pure Domain Layer.
 *
 * <p>There is no {@code deleteById} — a window is closed by a terminal status transition
 * ({@link WindowStatus#COMPLETED} or {@link WindowStatus#CANCELLED}), never physically removed.
 */
public interface OperationWindowRepository {

    Mono<OperationWindow> create(OperationWindow window);

    Mono<OperationWindow> findById(UUID id);

    /**
     * Tenant-scoped listing.
     *
     * @param status  optional status filter ({@code null} = any)
     * @param assetId optional target-asset filter ({@code null} = any)
     */
    Flux<OperationWindow> findAllByOrganisationId(UUID organisationId, WindowStatus status, UUID assetId);

    /**
     * Coarse candidate query for the collision check: every <b>active</b> window of the tenant whose
     * interval overlaps {@code [startAt, endAt)}. Asset intersection is decided by the domain service.
     */
    Flux<OperationWindow> findActiveOverlapping(UUID organisationId, Instant startAt, Instant endAt);

    Mono<Void> updateSchedule(UUID id, Instant startAt, Instant endAt);

    Mono<Void> updateStatus(UUID id, WindowStatus status);
}
