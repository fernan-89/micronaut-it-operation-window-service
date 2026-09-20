package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureCommentRequest;
import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.domain.exception.InvalidTicketStatusException;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTicketUseCaseTest {

    @Mock
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Mock
    private HashServicePort hashServicePort;

    private UUID ticketId;
    private UUID organisationId;
    private UUID assetId;
    private MaintenanceTicket ticket;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        ticket = MaintenanceTicket.createNew(ticketId, organisationId, assetId, "Broken fan", "Server fan making noise");
    }

    @Test
    void testInitiateMaintenanceTicketUseCase() {
        InitiateMaintenanceTicketUseCase useCase = new InitiateMaintenanceTicketUseCase(hashServicePort, maintenanceTicketRepository);
        InitiateMaintenanceTicketRequest request = new InitiateMaintenanceTicketRequest(assetId, "Broken fan", "Server fan making noise");

        when(hashServicePort.generateSovereignId("maintenance-ticket-creation")).thenReturn(Mono.just(ticketId));
        when(maintenanceTicketRepository.create(any(MaintenanceTicket.class))).thenReturn(Mono.just(ticket));

        StepVerifier.create(useCase.execute(organisationId, request))
                .assertNext(res -> {
                    assertEquals(ticketId, res.id());
                    assertEquals("OPEN", res.status());
                })
                .verifyComplete();
    }

    @Test
    void testRetrieveMaintenanceTicketUseCaseSuccess() {
        RetrieveMaintenanceTicketUseCase useCase = new RetrieveMaintenanceTicketUseCase(maintenanceTicketRepository);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));

        StepVerifier.create(useCase.execute(ticketId))
                .assertNext(res -> assertEquals(ticketId, res.id()))
                .verifyComplete();
    }

    @Test
    void testRetrieveMaintenanceTicketUseCaseNotFound() {
        RetrieveMaintenanceTicketUseCase useCase = new RetrieveMaintenanceTicketUseCase(maintenanceTicketRepository);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ticketId))
                .expectError(MaintenanceTicketNotFoundException.class)
                .verify();
    }

    @Test
    void testRetrieveMaintenanceTicketsUseCase() {
        RetrieveMaintenanceTicketsUseCase useCase = new RetrieveMaintenanceTicketsUseCase(maintenanceTicketRepository);
        when(maintenanceTicketRepository.findAllByOrganisationId(organisationId, null)).thenReturn(Flux.just(ticket));

        StepVerifier.create(useCase.execute(organisationId, null))
                .assertNext(res -> assertEquals(ticketId, res.id()))
                .verifyComplete();
    }

    @Test
    void testControlMaintenanceTicketUseCase() {
        ControlMaintenanceTicketUseCase useCase = new ControlMaintenanceTicketUseCase(maintenanceTicketRepository);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));
        when(maintenanceTicketRepository.updateStatus(ticketId, TicketStatus.IN_ANALYSIS)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ticketId, TicketStatus.IN_ANALYSIS))
                .verifyComplete();
    }

    @Test
    void testControlMaintenanceTicketUseCaseRejectsIllegalTransition() {
        ControlMaintenanceTicketUseCase useCase = new ControlMaintenanceTicketUseCase(maintenanceTicketRepository);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));

        StepVerifier.create(useCase.execute(ticketId, TicketStatus.COMPLETED))
                .expectError(InvalidTicketStatusException.class)
                .verify();
    }

    @Test
    void testCaptureMaintenanceCommentUseCaseWithoutTransition() {
        CaptureMaintenanceCommentUseCase useCase = new CaptureMaintenanceCommentUseCase(maintenanceTicketRepository);
        CaptureCommentRequest request = new CaptureCommentRequest("Investigating", null);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));
        when(maintenanceTicketRepository.appendComment(eq(ticketId), any(), eq(TicketStatus.OPEN))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ticketId, "tech-01", request))
                .verifyComplete();
    }

    @Test
    void testCaptureMaintenanceCommentUseCaseAdvancesFsm() {
        CaptureMaintenanceCommentUseCase useCase = new CaptureMaintenanceCommentUseCase(maintenanceTicketRepository);
        CaptureCommentRequest request = new CaptureCommentRequest("Starting analysis", TicketStatus.IN_ANALYSIS);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));
        when(maintenanceTicketRepository.appendComment(eq(ticketId), any(), eq(TicketStatus.IN_ANALYSIS))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ticketId, "tech-01", request))
                .verifyComplete();
    }

    @Test
    void testCaptureMaintenanceCommentUseCaseRejectsIllegalTargetStatus() {
        CaptureMaintenanceCommentUseCase useCase = new CaptureMaintenanceCommentUseCase(maintenanceTicketRepository);
        CaptureCommentRequest request = new CaptureCommentRequest("Trying to skip ahead", TicketStatus.COMPLETED);
        when(maintenanceTicketRepository.findById(ticketId)).thenReturn(Mono.just(ticket));

        StepVerifier.create(useCase.execute(ticketId, "tech-01", request))
                .expectError(InvalidTicketStatusException.class)
                .verify();
    }
}
