package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.mapper.MaintenanceTicketMapper;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Orchestrates the tenant-scoped listing of MaintenanceTickets (BIAN Behavior Qualifier:
 * {@code retrieve}, collection form).
 */
@Singleton
public class RetrieveMaintenanceTicketsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveMaintenanceTicketsUseCase.class);

    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public RetrieveMaintenanceTicketsUseCase(MaintenanceTicketRepository maintenanceTicketRepository) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    public Flux<MaintenanceTicketResponse> execute(UUID organisationId, @Nullable TicketStatus status) {
        log.info("[USE CASE] Listing maintenance tickets for organisation {} [status={}]", organisationId, status);

        return maintenanceTicketRepository.findAllByOrganisationId(organisationId, status)
                .map(MaintenanceTicketMapper::toResponse);
    }
}
