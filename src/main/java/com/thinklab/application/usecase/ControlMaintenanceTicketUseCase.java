package com.thinklab.application.usecase;

import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing direct MaintenanceTicket lifecycle control (BIAN Behavior Qualifier:
 * {@code control}), independent of the comment-driven advancement path.
 *
 * <p>Loads the aggregate, delegates the transition to the domain model (which throws
 * {@link com.thinklab.domain.exception.InvalidTicketStatusException} on an illegal move), and
 * only then issues the persistence update.
 */
@Singleton
public class ControlMaintenanceTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlMaintenanceTicketUseCase.class);

    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public ControlMaintenanceTicketUseCase(MaintenanceTicketRepository maintenanceTicketRepository) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    public Mono<Void> execute(UUID id, TicketStatus targetStatus) {
        log.info("[USE CASE] Controlling maintenance ticket lifecycle: {} for ID: {}", targetStatus, id);

        return maintenanceTicketRepository.findById(id)
                .switchIfEmpty(Mono.error(new MaintenanceTicketNotFoundException(id)))
                .flatMap(ticket -> {
                    ticket.changeStatus(targetStatus);
                    return maintenanceTicketRepository.updateStatus(id, targetStatus);
                });
    }
}
