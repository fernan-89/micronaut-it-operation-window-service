package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CaptureCommentRequest;
import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.usecase.CaptureMaintenanceCommentUseCase;
import com.thinklab.application.usecase.ControlMaintenanceTicketUseCase;
import com.thinklab.application.usecase.InitiateMaintenanceTicketUseCase;
import com.thinklab.application.usecase.RetrieveMaintenanceTicketUseCase;
import com.thinklab.application.usecase.RetrieveMaintenanceTicketsUseCase;
import com.thinklab.domain.exception.InvalidTicketStatusException;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTicketControllerTest {

    private static final String EXECUTOR = "tech-1";

    @Mock private InitiateMaintenanceTicketUseCase initiateUseCase;
    @Mock private RetrieveMaintenanceTicketUseCase retrieveUseCase;
    @Mock private RetrieveMaintenanceTicketsUseCase retrieveAllUseCase;
    @Mock private CaptureMaintenanceCommentUseCase captureCommentUseCase;
    @Mock private ControlMaintenanceTicketUseCase controlUseCase;

    @InjectMocks private MaintenanceTicketController controller;

    private UUID organisationId;
    private UUID ticketId;
    private MaintenanceTicketResponse sample;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        sample = new MaintenanceTicketResponse(ticketId, organisationId, UUID.randomUUID(), "Fan noise", "desc", "OPEN",
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("the controller is mounted as a subordinate resource of the Operation Window domain")
    void mountedPath() {
        assertEquals("/it-operation-window/v1/maintenance-ticket", MaintenanceTicketController.class.getAnnotation(Controller.class).value());
    }

    @Test
    @DisplayName("initiate returns 201 Created and rejects a malformed tenant header")
    void initiate() {
        InitiateMaintenanceTicketRequest request = new InitiateMaintenanceTicketRequest(UUID.randomUUID(), "Fan noise", "desc");
        when(initiateUseCase.execute(organisationId, request)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.initiate(organisationId.toString(), EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();
        assertThrows(IllegalArgumentException.class, () -> controller.initiate("bad", EXECUTOR, request));
    }

    @Test
    @DisplayName("retrieveById returns 200 OK and surfaces 404")
    void retrieveById() {
        UUID missing = UUID.randomUUID();
        when(retrieveUseCase.execute(ticketId)).thenReturn(Mono.just(sample));
        when(retrieveUseCase.execute(missing)).thenReturn(Mono.error(new MaintenanceTicketNotFoundException(missing)));

        StepVerifier.create(controller.retrieveById(ticketId)).assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveById(missing)).expectError(MaintenanceTicketNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveAll scopes by tenant with and without a status filter")
    void retrieveAll() {
        when(retrieveAllUseCase.execute(organisationId, TicketStatus.OPEN)).thenReturn(Flux.just(sample));
        when(retrieveAllUseCase.execute(organisationId, null)).thenReturn(Flux.just(sample, sample));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), TicketStatus.OPEN)).expectNext(List.of(sample)).verifyComplete();
        StepVerifier.create(controller.retrieveAll(organisationId.toString(), null)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveAll("bad", null)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("initiateComment delegates with the executor")
    void initiateComment() {
        CaptureCommentRequest request = new CaptureCommentRequest("checked the fan", TicketStatus.IN_ANALYSIS);
        when(captureCommentUseCase.execute(ticketId, EXECUTOR, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.initiateComment(ticketId, EXECUTOR, request)).verifyComplete();

        verify(captureCommentUseCase).execute(ticketId, EXECUTOR, request);
    }

    @Test
    @DisplayName("control returns 204 and surfaces an illegal transition")
    void control() {
        when(controlUseCase.execute(ticketId, TicketStatus.IN_ANALYSIS)).thenReturn(Mono.empty());
        when(controlUseCase.execute(ticketId, TicketStatus.COMPLETED)).thenReturn(Mono.error(new InvalidTicketStatusException("illegal")));

        StepVerifier.create(controller.control(ticketId, TicketStatus.IN_ANALYSIS, EXECUTOR))
                .assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.control(ticketId, TicketStatus.COMPLETED, EXECUTOR))
                .expectError(InvalidTicketStatusException.class).verify();
        verify(controlUseCase, org.mockito.Mockito.never()).execute(any(), org.mockito.ArgumentMatchers.eq(TicketStatus.AWAITING_PARTS));
    }
}
