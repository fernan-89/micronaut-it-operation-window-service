package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.mapper.MaintenanceTicketMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the business flow for MaintenanceTicket creation (BIAN Behavior Qualifier: {@code initiate}).
 */
@Singleton
public class InitiateMaintenanceTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateMaintenanceTicketUseCase.class);

    private final HashServicePort hashServicePort;
    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public InitiateMaintenanceTicketUseCase(HashServicePort hashServicePort, MaintenanceTicketRepository maintenanceTicketRepository) {
        this.hashServicePort = hashServicePort;
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    public Mono<MaintenanceTicketResponse> execute(UUID organisationId, InitiateMaintenanceTicketRequest request) {
        log.info("[USE CASE] Initiating maintenance ticket for organisation {} / asset {}", organisationId, request.assetId());

        return hashServicePort.generateSovereignId("maintenance-ticket-creation")
                .map(sovereignId -> MaintenanceTicketMapper.toDomain(request, organisationId, sovereignId))
                .flatMap(maintenanceTicketRepository::create)
                .map(saved -> {
                    log.info("[USE CASE] MaintenanceTicket successfully created with ID: {}", saved.getId());
                    return MaintenanceTicketMapper.toResponse(saved);
                });
    }
}
