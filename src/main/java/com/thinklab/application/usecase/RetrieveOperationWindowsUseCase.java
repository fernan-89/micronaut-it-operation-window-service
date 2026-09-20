package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.mapper.OperationWindowMapper;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.repository.OperationWindowRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Orchestrates the tenant-scoped listing of Operation Windows (BIAN Behavior Qualifier:
 * {@code retrieve} — collection), optionally filtered by status and/or target asset.
 */
@Singleton
public class RetrieveOperationWindowsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveOperationWindowsUseCase.class);

    private final OperationWindowRepository repository;

    public RetrieveOperationWindowsUseCase(OperationWindowRepository repository) {
        this.repository = repository;
    }

    public Flux<OperationWindowResponse> execute(UUID organisationId, WindowStatus status, UUID assetId) {
        log.info("[USE CASE] Retrieving operation windows for organisation: {} status: {} asset: {}", organisationId, status, assetId);

        return repository.findAllByOrganisationId(organisationId, status, assetId)
                .map(OperationWindowMapper::toResponse);
    }
}
