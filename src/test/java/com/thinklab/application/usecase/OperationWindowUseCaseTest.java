package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.EvaluateWindowCollisionRequest;
import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.request.RescheduleOperationWindowRequest;
import com.thinklab.domain.exception.InvalidWindowStatusException;
import com.thinklab.domain.exception.WindowCollisionException;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.OperationWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWindowUseCaseTest {

    private static final String EXECUTOR = "planner-1";

    @Mock private OperationWindowRepository repository;
    @Mock private HashServicePort hashServicePort;

    private WindowCollisionGuard guard;
    private UUID organisationId;
    private UUID assetA;
    private Instant start;
    private Instant end;
    private OperationWindow existing;

    @BeforeEach
    void setUp() {
        guard = new WindowCollisionGuard(repository);
        organisationId = UUID.randomUUID();
        assetA = UUID.randomUUID();
        start = Instant.now().plus(Duration.ofDays(1));
        end = start.plus(Duration.ofHours(2));
        existing = OperationWindow.createNew(UUID.randomUUID(), organisationId, "Existing", null, WindowType.PATCHING,
                Set.of(assetA), start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(3)), null, EXECUTOR);
    }

    private InitiateOperationWindowRequest request() {
        return new InitiateOperationWindowRequest("Firmware", "desc", WindowType.PATCHING, Set.of(assetA), start, end, null);
    }

    // ---------------------------------------------------------------- initiate

    @Test
    @DisplayName("Initiate: a free interval gets a sovereign ID and is persisted as SCHEDULED")
    void initiateSuccess() {
        UUID sovereign = UUID.randomUUID();
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(sovereign));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, request(), EXECUTOR))
                .assertNext(response -> {
                    assertEquals(sovereign, response.id());
                    assertEquals(organisationId, response.organisationId());
                    assertEquals("SCHEDULED", response.status());
                    assertEquals("PATCHING", response.windowType());
                    assertEquals(EXECUTOR, response.requestedBy());
                    assertEquals(Set.of(assetA), response.targetAssetIds());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Initiate: a collision fails with the full impact and never reaches the hash service or the repository")
    void initiateCollision() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(existing));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, request(), EXECUTOR))
                .expectErrorSatisfies(error -> {
                    WindowCollisionException collision = (WindowCollisionException) error;
                    assertEquals("ERR-COL-00409", collision.getErrorCode());
                    assertEquals(1, collision.getConflicts().size());
                    assertEquals(existing.getId(), collision.getConflicts().get(0).windowId());
                    assertEquals(Set.of(assetA), collision.getConflicts().get(0).overlappingAssetIds());
                })
                .verify();

        verifyNoInteractions(hashServicePort);
        verify(repository, never()).create(any());
    }

    private OperationWindow freeze() {
        return OperationWindow.createNew(UUID.randomUUID(), organisationId, "Year-end freeze", null, WindowType.CHANGE_FREEZE,
                Set.of(assetA), start.minus(Duration.ofHours(1)), end.plus(Duration.ofHours(1)), null, EXECUTOR);
    }

    private InitiateOperationWindowRequest overrideRequest(WindowType type, String justification) {
        return new InitiateOperationWindowRequest("Emergency fix", "desc", type, Set.of(assetA), start, end, null, justification);
    }

    @Test
    @DisplayName("Initiate: a DEPLOYMENT window with a justification is reserved over a CHANGE_FREEZE and records the override in its description")
    void initiateOverridesChangeFreeze() {
        UUID sovereign = UUID.randomUUID();
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(freeze()));
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(sovereign));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, "P1 outage, ECAB approved"), EXECUTOR))
                .assertNext(response -> {
                    assertEquals(sovereign, response.id());
                    assertTrue(response.description().contains("[CHANGE_FREEZE OVERRIDE] P1 outage, ECAB approved"));
                    assertTrue(response.description().startsWith("desc"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Initiate: the override note stands alone when the window has no description")
    void initiateOverrideWithoutDescription() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateOperationWindowRequest noDescription = new InitiateOperationWindowRequest("Emergency fix", null,
                WindowType.DEPLOYMENT, Set.of(assetA), start, end, null, "P1 outage");

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, noDescription, EXECUTOR))
                .assertNext(response -> assertEquals("[CHANGE_FREEZE OVERRIDE] P1 outage", response.description()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Initiate: a blank description is replaced by the override note")
    void initiateOverrideWithBlankDescription() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateOperationWindowRequest blank = new InitiateOperationWindowRequest("Emergency fix", "  ",
                WindowType.DEPLOYMENT, Set.of(assetA), start, end, null, "P1 outage");

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, blank, EXECUTOR))
                .assertNext(response -> assertEquals("[CHANGE_FREEZE OVERRIDE] P1 outage", response.description()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Initiate: the override skips only CHANGE_FREEZE windows - a colliding non-freeze window still blocks")
    void initiateOverrideStillCollidesWithOtherWindows() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(freeze(), existing));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, "P1 outage"), EXECUTOR))
                .expectErrorSatisfies(error -> {
                    WindowCollisionException collision = (WindowCollisionException) error;
                    assertEquals(1, collision.getConflicts().size());
                    assertEquals(existing.getId(), collision.getConflicts().get(0).windowId());
                })
                .verify();
    }

    @Test
    @DisplayName("Initiate: without a justification a CHANGE_FREEZE still blocks a DEPLOYMENT window")
    void initiateWithoutOverrideIsBlockedByFreeze() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(freeze()));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, null), EXECUTOR))
                .expectError(WindowCollisionException.class)
                .verify();
    }

    @Test
    @DisplayName("Initiate: a blank justification, or an override on a non-DEPLOYMENT window, is refused (400) before any I/O")
    void initiateRefusesInvalidOverride() {
        InitiateOperationWindowUseCase useCase = new InitiateOperationWindowUseCase(hashServicePort, repository, guard);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, "  "), EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(organisationId, overrideRequest(WindowType.PATCHING, "P1 outage"), EXECUTOR));

        verifyNoInteractions(repository, hashServicePort);
    }

    @Test
    @DisplayName("Initiate: a freeze override from a role below ADMIN/SERVICE is refused (403) before any I/O; ADMIN, SERVICE and no role (security off) are allowed")
    void initiateOverrideNeedsAnElevatedRole() {
        InitiateOperationWindowUseCase useCase = new InitiateOperationWindowUseCase(hashServicePort, repository, guard);

        for (String role : new String[]{"OPERATOR", "REQUESTER", "VIEWER"}) {
            StepVerifier.create(useCase.execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, "P1 outage"), EXECUTOR, role))
                    .expectErrorSatisfies(error -> assertEquals("ERR-WIN-00403", ((com.thinklab.domain.exception.FreezeOverrideNotPermittedException) error).getErrorCode()))
                    .verify();
        }
        verifyNoInteractions(repository, hashServicePort);

        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        for (String role : new String[]{"ADMIN", "SERVICE", null}) {
            StepVerifier.create(useCase.execute(organisationId, overrideRequest(WindowType.DEPLOYMENT, "P1 outage"), EXECUTOR, role)).expectNextCount(1).verifyComplete();
        }
    }

    @Test
    @DisplayName("Initiate: a window with no override needs no elevated role")
    void initiateWithoutOverrideNeedsNoRole() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId("operation-window-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any(OperationWindow.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard).execute(organisationId, request(), EXECUTOR, "VIEWER"))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("Initiate: an invalid interval fails fast (400) before any I/O")
    void initiateInvalidIntervalFailsFast() {
        InitiateOperationWindowRequest bad = new InitiateOperationWindowRequest("t", null, WindowType.PATCHING, Set.of(assetA), end, start, null);

        assertThrows(IllegalArgumentException.class, () ->
                new InitiateOperationWindowUseCase(hashServicePort, repository, guard).execute(organisationId, bad, EXECUTOR));

        verifyNoInteractions(repository, hashServicePort);
    }

    @Test
    @DisplayName("Initiate: a hash-service failure is propagated and nothing is persisted")
    void initiateHashFailure() {
        when(repository.findActiveOverlapping(any(), any(), any())).thenReturn(Flux.empty());
        when(hashServicePort.generateSovereignId(anyString())).thenReturn(Mono.error(new IllegalStateException("hash down")));

        StepVerifier.create(new InitiateOperationWindowUseCase(hashServicePort, repository, guard)
                        .execute(organisationId, request(), EXECUTOR))
                .expectErrorMessage("hash down")
                .verify();

        verify(repository, never()).create(any());
    }

    // ---------------------------------------------------------------- retrieve

    @Test
    @DisplayName("Retrieve: projects the aggregate, or fails with 404")
    void retrieve() {
        UUID id = existing.getId();
        when(repository.findById(id)).thenReturn(Mono.just(existing)).thenReturn(Mono.empty());
        RetrieveOperationWindowUseCase useCase = new RetrieveOperationWindowUseCase(repository);

        StepVerifier.create(useCase.execute(id))
                .assertNext(response -> assertEquals("Existing", response.title()))
                .verifyComplete();
        StepVerifier.create(useCase.execute(id))
                .expectErrorSatisfies(error -> assertEquals("ERR-WIN-00404", ((WindowNotFoundException) error).getErrorCode()))
                .verify();
    }

    @Test
    @DisplayName("Retrieve collection: forwards tenant, status and asset filters")
    void retrieveCollection() {
        when(repository.findAllByOrganisationId(organisationId, WindowStatus.SCHEDULED, assetA)).thenReturn(Flux.just(existing));
        when(repository.findAllByOrganisationId(organisationId, null, null)).thenReturn(Flux.empty());
        RetrieveOperationWindowsUseCase useCase = new RetrieveOperationWindowsUseCase(repository);

        StepVerifier.create(useCase.execute(organisationId, WindowStatus.SCHEDULED, assetA)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(organisationId, null, null)).verifyComplete();
    }

    // ---------------------------------------------------------------- reschedule

    @Test
    @DisplayName("Reschedule: a free new slot is validated, checked excluding the window itself, then persisted")
    void rescheduleSuccess() {
        UUID id = existing.getId();
        Instant newStart = start.plus(Duration.ofDays(2));
        Instant newEnd = newStart.plus(Duration.ofHours(1));
        when(repository.findById(id)).thenReturn(Mono.just(existing));
        when(repository.findActiveOverlapping(eq(organisationId), eq(newStart), eq(newEnd))).thenReturn(Flux.just(existing));
        when(repository.updateSchedule(id, newStart, newEnd)).thenReturn(Mono.empty());

        StepVerifier.create(new RescheduleOperationWindowUseCase(repository, guard)
                        .execute(id, new RescheduleOperationWindowRequest(newStart, newEnd)))
                .verifyComplete();

        verify(repository).updateSchedule(id, newStart, newEnd);
    }

    @Test
    @DisplayName("Reschedule: colliding with ANOTHER window fails with 409 and never writes")
    void rescheduleCollision() {
        UUID id = existing.getId();
        OperationWindow other = OperationWindow.createNew(UUID.randomUUID(), organisationId, "Other", null, WindowType.MIGRATION,
                Set.of(assetA), start.plus(Duration.ofDays(2)), start.plus(Duration.ofDays(2)).plus(Duration.ofHours(2)), null, EXECUTOR);
        when(repository.findById(id)).thenReturn(Mono.just(existing));
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(other));

        StepVerifier.create(new RescheduleOperationWindowUseCase(repository, guard)
                        .execute(id, new RescheduleOperationWindowRequest(start.plus(Duration.ofDays(2)), start.plus(Duration.ofDays(2)).plus(Duration.ofHours(1)))))
                .expectError(WindowCollisionException.class)
                .verify();

        verify(repository, never()).updateSchedule(any(), any(), any());
    }

    @Test
    @DisplayName("Reschedule: 404 when the window does not exist")
    void rescheduleNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(new RescheduleOperationWindowUseCase(repository, guard)
                        .execute(id, new RescheduleOperationWindowRequest(start, end)))
                .expectError(WindowNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Reschedule: a window that already started cannot move (409) and never writes")
    void rescheduleNotScheduled() {
        existing.start();
        when(repository.findById(existing.getId())).thenReturn(Mono.just(existing));

        StepVerifier.create(new RescheduleOperationWindowUseCase(repository, guard)
                        .execute(existing.getId(), new RescheduleOperationWindowRequest(start, end)))
                .expectError(InvalidWindowStatusException.class)
                .verify();

        verify(repository, never()).updateSchedule(any(), any(), any());
    }

    // ---------------------------------------------------------------- control

    @Test
    @DisplayName("Control: START then COMPLETE persist the matching statuses")
    void controlLifecycle() {
        UUID id = existing.getId();
        when(repository.findById(id)).thenReturn(Mono.just(existing));
        when(repository.updateStatus(id, WindowStatus.IN_PROGRESS)).thenReturn(Mono.empty());
        when(repository.updateStatus(id, WindowStatus.COMPLETED)).thenReturn(Mono.empty());
        ControlOperationWindowUseCase useCase = new ControlOperationWindowUseCase(repository);

        StepVerifier.create(useCase.execute(id, ControlOperationWindowUseCase.Action.START)).verifyComplete();
        StepVerifier.create(useCase.execute(id, ControlOperationWindowUseCase.Action.COMPLETE)).verifyComplete();

        verify(repository).updateStatus(id, WindowStatus.IN_PROGRESS);
        verify(repository).updateStatus(id, WindowStatus.COMPLETED);
    }

    @Test
    @DisplayName("Control: CANCEL persists the terminal status")
    void controlCancel() {
        UUID id = existing.getId();
        when(repository.findById(id)).thenReturn(Mono.just(existing));
        when(repository.updateStatus(id, WindowStatus.CANCELLED)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlOperationWindowUseCase(repository).execute(id, ControlOperationWindowUseCase.Action.CANCEL))
                .verifyComplete();
    }

    @Test
    @DisplayName("Control: an illegal move never reaches the repository; a missing window is 404")
    void controlFailures() {
        UUID id = existing.getId();
        when(repository.findById(id)).thenReturn(Mono.just(existing)).thenReturn(Mono.empty());
        ControlOperationWindowUseCase useCase = new ControlOperationWindowUseCase(repository);

        StepVerifier.create(useCase.execute(id, ControlOperationWindowUseCase.Action.COMPLETE))
                .expectError(InvalidWindowStatusException.class).verify();
        verify(repository, never()).updateStatus(any(), any());

        StepVerifier.create(useCase.execute(id, ControlOperationWindowUseCase.Action.START))
                .expectError(WindowNotFoundException.class).verify();
    }

    @Test
    @DisplayName("Control: each action maps to its target status")
    void controlTargets() {
        assertEquals(WindowStatus.IN_PROGRESS, ControlOperationWindowUseCase.Action.START.targetStatus());
        assertEquals(WindowStatus.COMPLETED, ControlOperationWindowUseCase.Action.COMPLETE.targetStatus());
        assertEquals(WindowStatus.CANCELLED, ControlOperationWindowUseCase.Action.CANCEL.targetStatus());
    }

    // ---------------------------------------------------------------- evaluate (dry-run)

    @Test
    @DisplayName("Evaluate: reports collides=true with the impact, without persisting anything")
    void evaluateCollides() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(existing));

        StepVerifier.create(new EvaluateWindowCollisionUseCase(guard)
                        .execute(organisationId, new EvaluateWindowCollisionRequest(Set.of(assetA), start, end, null)))
                .assertNext(response -> {
                    assertTrue(response.collides());
                    assertEquals(1, response.conflicts().size());
                    assertEquals(existing.getId(), response.conflicts().get(0).windowId());
                    assertEquals(Set.of(assetA), response.conflicts().get(0).overlappingAssetIds());
                })
                .verifyComplete();

        verify(repository, never()).create(any());
    }

    @Test
    @DisplayName("Evaluate: reports collides=false for a free slot and honours excludeWindowId")
    void evaluateFree() {
        when(repository.findActiveOverlapping(eq(organisationId), any(), any())).thenReturn(Flux.just(existing));

        StepVerifier.create(new EvaluateWindowCollisionUseCase(guard)
                        .execute(organisationId, new EvaluateWindowCollisionRequest(Set.of(assetA), start, end, existing.getId())))
                .assertNext(response -> {
                    assertFalse(response.collides());
                    assertTrue(response.conflicts().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Evaluate: an invalid interval is surfaced as an error signal (400), not thrown synchronously")
    void evaluateInvalid() {
        StepVerifier.create(new EvaluateWindowCollisionUseCase(guard)
                        .execute(organisationId, new EvaluateWindowCollisionRequest(Set.of(assetA), end, start, null)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------- guard

    @Test
    @DisplayName("Guard: assertFree completes on a free slot and errors with the conflicts otherwise")
    void guardBehaviour() {
        when(repository.findActiveOverlapping(any(), any(), any())).thenReturn(Flux.empty()).thenReturn(Flux.just(existing));

        StepVerifier.create(guard.assertFree(organisationId, null, Set.of(assetA), start, end)).verifyComplete();
        StepVerifier.create(guard.assertFree(organisationId, null, Set.of(assetA), start, end))
                .expectErrorSatisfies(error -> assertEquals(1, ((WindowCollisionException) error).getConflicts().size()))
                .verify();
    }

    @Test
    @DisplayName("WindowCollisionException copies its conflicts defensively")
    void collisionExceptionIsImmutable() {
        WindowCollisionException exception = new WindowCollisionException(java.util.List.of(
                new com.thinklab.domain.service.WindowCollisionChecker.WindowConflict(UUID.randomUUID(), "x", start, end, Set.of(assetA))));

        assertThrows(UnsupportedOperationException.class, () -> exception.getConflicts().clear());
        assertTrue(exception.getMessage().contains("1 active window"));
    }
}
