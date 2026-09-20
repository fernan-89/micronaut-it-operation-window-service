package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.mapper.MaintenanceTicketMapper;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates single MaintenanceTicket retrieval (BIAN Behavior Qualifier: {@code retrieve}).
 */
@Singleton
public class RetrieveMaintenanceTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveMaintenanceTicketUseCase.class);

    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public RetrieveMaintenanceTicketUseCase(MaintenanceTicketRepository maintenanceTicketRepository) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    public Mono<MaintenanceTicketResponse> execute(UUID id) {
        log.info("[USE CASE] Retrieving maintenance ticket by ID: {}", id);

        return maintenanceTicketRepository.findById(id)
                .switchIfEmpty(Mono.error(new MaintenanceTicketNotFoundException(id)))
                .map(MaintenanceTicketMapper::toResponse);
    }
}
