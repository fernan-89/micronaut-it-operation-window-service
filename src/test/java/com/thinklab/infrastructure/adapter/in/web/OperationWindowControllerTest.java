package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.EvaluateWindowCollisionRequest;
import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.request.RescheduleOperationWindowRequest;
import com.thinklab.application.dto.response.CollisionEvaluationResponse;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.usecase.ControlOperationWindowUseCase;
import com.thinklab.application.usecase.EvaluateWindowCollisionUseCase;
import com.thinklab.application.usecase.InitiateOperationWindowUseCase;
import com.thinklab.application.usecase.RescheduleOperationWindowUseCase;
import com.thinklab.application.usecase.RetrieveOperationWindowUseCase;
import com.thinklab.application.usecase.RetrieveOperationWindowsUseCase;
import com.thinklab.domain.exception.InvalidWindowStatusException;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import io.micronaut.http.HttpStatus;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWindowControllerTest {

    private static final String EXECUTOR = "planner-1";

    @Mock private InitiateOperationWindowUseCase initiateUseCase;
    @Mock private RetrieveOperationWindowUseCase retrieveUseCase;
    @Mock private RetrieveOperationWindowsUseCase retrieveAllUseCase;
    @Mock private RescheduleOperationWindowUseCase rescheduleUseCase;
    @Mock private ControlOperationWindowUseCase controlUseCase;
    @Mock private EvaluateWindowCollisionUseCase evaluateUseCase;

    @InjectMocks private OperationWindowController controller;

    private UUID organisationId;
    private UUID windowId;
    private UUID assetId;
    private Instant start;
    private Instant end;
    private OperationWindowResponse sample;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        windowId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        start = Instant.now().plus(Duration.ofDays(1));
        end = start.plus(Duration.ofHours(1));
        sample = new OperationWindowResponse(windowId, organisationId, "Firmware", "d", "PATCHING", Set.of(assetId),
                start, end, "SCHEDULED", null, EXECUTOR, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created")
    void initiate() {
        InitiateOperationWindowRequest request = new InitiateOperationWindowRequest("Firmware", "d", WindowType.PATCHING, Set.of(assetId), start, end, null);
        when(initiateUseCase.execute(organisationId, request, EXECUTOR)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.initiate(organisationId.toString(), EXECUTOR, request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatus());
                    assertEquals(windowId, response.body().id());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("initiate rejects a malformed tenant header before reaching the use case")
    void initiateMalformedTenant() {
        InitiateOperationWindowRequest request = new InitiateOperationWindowRequest("t", null, WindowType.PATCHING, Set.of(assetId), start, end, null);

        assertThrows(IllegalArgumentException.class, () -> controller.initiate("nope", EXECUTOR, request));
    }

    @Test
    @DisplayName("retrieveById returns 200 OK and surfaces 404")
    void retrieveById() {
        when(retrieveUseCase.execute(windowId)).thenReturn(Mono.just(sample));
        UUID missing = UUID.randomUUID();
        when(retrieveUseCase.execute(missing)).thenReturn(Mono.error(new WindowNotFoundException(missing)));

        StepVerifier.create(controller.retrieveById(windowId))
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus()))
                .verifyComplete();
        StepVerifier.create(controller.retrieveById(missing)).expectError(WindowNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveAll scopes by tenant and forwards the status and asset filters")
    void retrieveAll() {
        when(retrieveAllUseCase.execute(organisationId, WindowStatus.SCHEDULED, assetId)).thenReturn(Flux.just(sample));
        when(retrieveAllUseCase.execute(organisationId, null, null)).thenReturn(Flux.just(sample, sample));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), WindowStatus.SCHEDULED, assetId)).expectNext(List.of(sample)).verifyComplete();
        StepVerifier.create(controller.retrieveAll(organisationId.toString(), null, null)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveAll("bad", null, null)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("reschedule returns 204 No Content")
    void reschedule() {
        RescheduleOperationWindowRequest request = new RescheduleOperationWindowRequest(start, end);
        when(rescheduleUseCase.execute(windowId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.reschedule(windowId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("each control endpoint dispatches its action and returns 204")
    void controlEndpoints() {
        when(controlUseCase.execute(eq(windowId), any(ControlOperationWindowUseCase.Action.class))).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlStart(windowId, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlComplete(windowId, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlCancel(windowId, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();

        verify(controlUseCase).execute(windowId, ControlOperationWindowUseCase.Action.START);
        verify(controlUseCase).execute(windowId, ControlOperationWindowUseCase.Action.COMPLETE);
        verify(controlUseCase).execute(windowId, ControlOperationWindowUseCase.Action.CANCEL);
    }

    @Test
    @DisplayName("a control endpoint surfaces an illegal transition")
    void controlIllegal() {
        when(controlUseCase.execute(windowId, ControlOperationWindowUseCase.Action.COMPLETE))
                .thenReturn(Mono.error(new InvalidWindowStatusException("illegal")));

        StepVerifier.create(controller.controlComplete(windowId, EXECUTOR)).expectError(InvalidWindowStatusException.class).verify();
    }

    @Test
    @DisplayName("evaluateCollision returns 200 OK with the dry-run result")
    void evaluateCollision() {
        EvaluateWindowCollisionRequest request = new EvaluateWindowCollisionRequest(Set.of(assetId), start, end, null);
        CollisionEvaluationResponse result = new CollisionEvaluationResponse(false, List.of());
        when(evaluateUseCase.execute(organisationId, request)).thenReturn(Mono.just(result));

        StepVerifier.create(controller.evaluateCollision(organisationId.toString(), request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(false, response.body().collides());
                })
                .verifyComplete();
        assertThrows(IllegalArgumentException.class, () -> controller.evaluateCollision("bad", request));
    }
}
