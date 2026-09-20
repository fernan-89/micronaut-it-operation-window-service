package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureCommentRequest;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Use Case governing forensic comment capture on a MaintenanceTicket (BIAN Behavior Qualifier:
 * {@code comment/initiate}).
 *
 * <p><b>State Machine Enforcement:</b> Loads the aggregate first and delegates the optional
 * status transition to {@link MaintenanceTicket#addComment(Comment, Optional)}, which validates
 * through the FSM exactly like {@code ControlOrganisationUseCase} does in the Party Reference
 * Data Directory Service Domain — never a raw partial update bypassing the domain model.
 */
@Singleton
public class CaptureMaintenanceCommentUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureMaintenanceCommentUseCase.class);

    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public CaptureMaintenanceCommentUseCase(MaintenanceTicketRepository maintenanceTicketRepository) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    public Mono<Void> execute(UUID id, String executor, CaptureCommentRequest request) {
        log.info("[USE CASE] Capturing comment on maintenance ticket {} [executor={}, targetStatus={}]", id, executor, request.targetStatus());

        return maintenanceTicketRepository.findById(id)
                .switchIfEmpty(Mono.error(new MaintenanceTicketNotFoundException(id)))
                .flatMap(ticket -> {
                    Comment comment = new Comment(UUID.randomUUID(), executor, request.text(), Instant.now());
                    Optional<com.thinklab.domain.model.MaintenanceTicket.TicketStatus> targetStatus = Optional.ofNullable(request.targetStatus());
                    ticket.addComment(comment, targetStatus);
                    return maintenanceTicketRepository.appendComment(id, comment, ticket.getStatus());
                });
    }
}
