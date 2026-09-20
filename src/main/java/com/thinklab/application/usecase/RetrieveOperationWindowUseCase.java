package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.mapper.OperationWindowMapper;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.repository.OperationWindowRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates single-window retrieval (BIAN Behavior Qualifier: {@code retrieve}).
 */
@Singleton
public class RetrieveOperationWindowUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveOperationWindowUseCase.class);

    private final OperationWindowRepository repository;

    public RetrieveOperationWindowUseCase(OperationWindowRepository repository) {
        this.repository = repository;
    }

    public Mono<OperationWindowResponse> execute(UUID id) {
        log.info("[USE CASE] Retrieving operation window ID: {}", id);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new WindowNotFoundException(id)))
                .map(OperationWindowMapper::toResponse);
    }
}
