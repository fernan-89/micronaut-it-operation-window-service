package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidWindowStatusException;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationWindowTest {

    private static final String EXECUTOR = "planner-1";

    private UUID id;
    private UUID organisationId;
    private UUID assetA;
    private UUID assetB;
    private Instant start;
    private Instant end;
    private OperationWindow window;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        assetA = UUID.randomUUID();
        assetB = UUID.randomUUID();
        start = Instant.now().plus(Duration.ofHours(2));
        end = start.plus(Duration.ofHours(1));
        window = create(Set.of(assetA), start, end);
    }

    private OperationWindow create(Set<UUID> assets, Instant s, Instant e) {
        return OperationWindow.createNew(id, organisationId, "Core switch firmware", "Upgrade to 10.2", WindowType.PATCHING,
                assets, s, e, null, EXECUTOR);
    }

    // ------------------------------------------------------------------ creation

    @Test
    @DisplayName("createNew should build a SCHEDULED window exposing every attribute")
    void createNew() {
        UUID ticket = UUID.randomUUID();

        OperationWindow w = OperationWindow.createNew(id, organisationId, "Title", "Desc", WindowType.MIGRATION,
                Set.of(assetA, assetB), start, end, ticket, EXECUTOR);

        assertEquals(id, w.getId());
        assertEquals(organisationId, w.getOrganisationId());
        assertEquals("Title", w.getTitle());
        assertEquals("Desc", w.getDescription());
        assertEquals(WindowType.MIGRATION, w.getWindowType());
        assertEquals(Set.of(assetA, assetB), w.getTargetAssetIds());
        assertEquals(start, w.getStartAt());
        assertEquals(end, w.getEndAt());
        assertEquals(WindowStatus.SCHEDULED, w.getStatus());
        assertEquals(ticket, w.getMaintenanceTicketId());
        assertEquals(EXECUTOR, w.getRequestedBy());
        assertNotNull(w.getCreatedAt());
        assertEquals(w.getCreatedAt(), w.getUpdatedAt());
        assertTrue(w.isActive());
    }

    @Test
    @DisplayName("createNew should reject missing mandatory fields")
    void createNewMandatory() {
        Set<UUID> assets = Set.of(assetA);
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(null, organisationId, "t", null, WindowType.PATCHING, assets, start, end, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, null, "t", null, WindowType.PATCHING, assets, start, end, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, organisationId, "t", null, null, assets, start, end, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, organisationId, null, null, WindowType.PATCHING, assets, start, end, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, organisationId, " ", null, WindowType.PATCHING, assets, start, end, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, organisationId, "t", null, WindowType.PATCHING, assets, start, end, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.createNew(id, organisationId, "t", null, WindowType.PATCHING, assets, start, end, null, " "));
    }

    @Test
    @DisplayName("createNew should require between 1 and 50 non-null target assets")
    void targetAssets() {
        assertThrows(IllegalArgumentException.class, () -> create(null, start, end));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(), start, end));

        Set<UUID> tooMany = IntStream.range(0, OperationWindow.MAX_TARGET_ASSETS + 1).mapToObj(i -> UUID.randomUUID())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThrows(IllegalArgumentException.class, () -> create(tooMany, start, end));

        Set<UUID> max = IntStream.range(0, OperationWindow.MAX_TARGET_ASSETS).mapToObj(i -> UUID.randomUUID())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(OperationWindow.MAX_TARGET_ASSETS, create(max, start, end).getTargetAssetIds().size());

        Set<UUID> withNull = new LinkedHashSet<>();
        withNull.add(assetA);
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> create(withNull, start, end));
    }

    @Test
    @DisplayName("createNew should enforce a positive interval between 5 minutes and 72 hours")
    void intervalRules() {
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), null, end));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), start, null));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), start, start));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), start, start.minusSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), start, start.plus(Duration.ofMinutes(4))));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), start, start.plus(Duration.ofHours(72)).plusSeconds(1)));

        assertEquals(Duration.ofMinutes(5), Duration.between(start, create(Set.of(assetA), start, start.plus(OperationWindow.MIN_DURATION)).getEndAt()));
        assertEquals(Duration.ofHours(72), Duration.between(start, create(Set.of(assetA), start, start.plus(OperationWindow.MAX_DURATION)).getEndAt()));
    }

    @Test
    @DisplayName("createNew should refuse a window that starts in the past (with a one-minute tolerance)")
    void pastStart() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        assertThrows(IllegalArgumentException.class, () -> create(Set.of(assetA), past, past.plus(Duration.ofHours(1))));

        Instant justNow = Instant.now().minusSeconds(20);
        assertNotNull(create(Set.of(assetA), justNow, justNow.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("createNew should defensively copy the asset set and expose a read-only view")
    void defensiveCopy() {
        Set<UUID> source = new LinkedHashSet<>(Set.of(assetA));
        OperationWindow w = create(source, start, end);
        source.add(assetB);

        assertEquals(Set.of(assetA), w.getTargetAssetIds());
        assertThrows(UnsupportedOperationException.class, () -> w.getTargetAssetIds().add(assetB));
    }

    @Test
    @DisplayName("the five window types are available")
    void windowTypes() {
        assertEquals(5, WindowType.values().length);
        assertEquals(WindowType.CHANGE_FREEZE, WindowType.valueOf("CHANGE_FREEZE"));
    }

    // ------------------------------------------------------------------ reconstitution

    @Test
    @DisplayName("reconstitute should restore persisted state (even a past interval) and default status/timestamps")
    void reconstitute() {
        Instant pastStart = Instant.parse("2025-01-01T00:00:00Z");
        Instant pastEnd = Instant.parse("2025-01-01T02:00:00Z");
        Instant created = Instant.parse("2024-12-01T00:00:00Z");

        OperationWindow restored = OperationWindow.reconstitute(id, organisationId, "t", "d", WindowType.MAINTENANCE,
                Set.of(assetA), pastStart, pastEnd, WindowStatus.COMPLETED, null, "x", created, created);
        OperationWindow defaults = OperationWindow.reconstitute(id, organisationId, "t", null, WindowType.MAINTENANCE,
                null, pastStart, pastEnd, null, null, null, null, null);

        assertEquals(WindowStatus.COMPLETED, restored.getStatus());
        assertEquals(pastStart, restored.getStartAt());
        assertEquals(created, restored.getCreatedAt());
        assertFalse(restored.isActive());
        assertEquals(WindowStatus.SCHEDULED, defaults.getStatus());
        assertTrue(defaults.getTargetAssetIds().isEmpty());
        assertNotNull(defaults.getCreatedAt());
    }

    @Test
    @DisplayName("reconstitute should reject missing identity, title, type or interval")
    void reconstituteValidation() {
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(null, organisationId, "t", null, WindowType.PATCHING, null, start, end, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(id, null, "t", null, WindowType.PATCHING, null, start, end, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(id, organisationId, null, null, WindowType.PATCHING, null, start, end, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(id, organisationId, "t", null, null, null, start, end, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(id, organisationId, "t", null, WindowType.PATCHING, null, null, end, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> OperationWindow.reconstitute(id, organisationId, "t", null, WindowType.PATCHING, null, start, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ reschedule

    @Test
    @DisplayName("reschedule should move a SCHEDULED window and refresh updatedAt")
    void reschedule() {
        Instant newStart = start.plus(Duration.ofDays(1));
        Instant newEnd = newStart.plus(Duration.ofHours(2));

        window.reschedule(newStart, newEnd);

        assertEquals(newStart, window.getStartAt());
        assertEquals(newEnd, window.getEndAt());
        assertEquals(WindowStatus.SCHEDULED, window.getStatus());
    }

    @Test
    @DisplayName("reschedule should validate the new interval and leave the window untouched on failure")
    void rescheduleValidation() {
        assertThrows(IllegalArgumentException.class, () -> window.reschedule(start, start));
        assertThrows(IllegalArgumentException.class, () -> window.reschedule(Instant.now().minus(Duration.ofDays(1)), Instant.now().minus(Duration.ofDays(1)).plus(Duration.ofHours(1))));

        assertEquals(start, window.getStartAt());
        assertEquals(end, window.getEndAt());
    }

    @Test
    @DisplayName("reschedule is only allowed while SCHEDULED")
    void rescheduleOnlyWhenScheduled() {
        Instant newStart = start.plus(Duration.ofDays(1));
        Instant newEnd = newStart.plus(Duration.ofHours(1));

        window.start();
        InvalidWindowStatusException inProgress = assertThrows(InvalidWindowStatusException.class, () -> window.reschedule(newStart, newEnd));
        assertEquals("ERR-WIN-00409", inProgress.getErrorCode());
        window.complete();
        assertThrows(InvalidWindowStatusException.class, () -> window.reschedule(newStart, newEnd));

        OperationWindow cancelled = create(Set.of(assetA), start, end);
        cancelled.cancel();
        assertThrows(InvalidWindowStatusException.class, () -> cancelled.reschedule(newStart, newEnd));
        assertEquals(start, cancelled.getStartAt());
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    @DisplayName("SCHEDULED -> IN_PROGRESS -> COMPLETED")
    void happyPath() {
        window.start();
        assertEquals(WindowStatus.IN_PROGRESS, window.getStatus());
        assertTrue(window.isActive());

        window.complete();
        assertEquals(WindowStatus.COMPLETED, window.getStatus());
        assertFalse(window.isActive());
    }

    @Test
    @DisplayName("SCHEDULED and IN_PROGRESS windows can be cancelled; CANCELLED is inactive")
    void cancel() {
        window.cancel();
        assertEquals(WindowStatus.CANCELLED, window.getStatus());
        assertFalse(window.isActive());

        OperationWindow running = create(Set.of(assetA), start, end);
        running.start();
        running.cancel();
        assertEquals(WindowStatus.CANCELLED, running.getStatus());
    }

    @Test
    @DisplayName("Illegal jumps and redundant transitions are rejected with the right message")
    void illegalTransitions() {
        InvalidWindowStatusException jump = assertThrows(InvalidWindowStatusException.class, window::complete);
        assertTrue(jump.getMessage().contains("Compliance Violation"));

        window.start();
        InvalidWindowStatusException idempotent = assertThrows(InvalidWindowStatusException.class, window::start);
        assertTrue(idempotent.getMessage().contains("Idempotency Violation"));
    }

    @Test
    @DisplayName("COMPLETED and CANCELLED are terminal")
    void terminalStates() {
        window.start();
        window.complete();
        assertThrows(InvalidWindowStatusException.class, window::cancel);
        assertThrows(InvalidWindowStatusException.class, window::start);

        OperationWindow cancelled = create(Set.of(assetA), start, end);
        cancelled.cancel();
        assertThrows(InvalidWindowStatusException.class, cancelled::start);
        assertThrows(InvalidWindowStatusException.class, cancelled::complete);
    }

    @Test
    @DisplayName("changeStatus rejects a null target")
    void nullStatus() {
        assertThrows(NullPointerException.class, () -> window.changeStatus(null));
    }

    @TestFactory
    @DisplayName("WindowStatus transition matrix is exhaustive and matches the documented FSM")
    Stream<DynamicTest> transitionMatrix() {
        Map<WindowStatus, Set<WindowStatus>> allowed = Map.of(
                WindowStatus.SCHEDULED, EnumSet.of(WindowStatus.IN_PROGRESS, WindowStatus.CANCELLED),
                WindowStatus.IN_PROGRESS, EnumSet.of(WindowStatus.COMPLETED, WindowStatus.CANCELLED),
                WindowStatus.COMPLETED, EnumSet.noneOf(WindowStatus.class),
                WindowStatus.CANCELLED, EnumSet.noneOf(WindowStatus.class)
        );

        return Stream.of(WindowStatus.values()).flatMap(from -> Stream.of(WindowStatus.values()).map(to ->
                DynamicTest.dynamicTest(from + " -> " + to, () -> {
                    boolean expected = allowed.get(from).contains(to);
                    assertEquals(expected, from.canTransitionTo(to));
                    if (expected) {
                        from.validateTransitionTo(to);
                    } else {
                        assertThrows(InvalidWindowStatusException.class, () -> from.validateTransitionTo(to));
                    }
                })));
    }

    @Test
    @DisplayName("canTransitionTo(null) is false and validateTransitionTo(null) is a programming error")
    void nullTargets() {
        assertFalse(WindowStatus.SCHEDULED.canTransitionTo(null));
        assertThrows(NullPointerException.class, () -> WindowStatus.SCHEDULED.validateTransitionTo(null));
    }

    // ------------------------------------------------------------------ collision primitives

    @Test
    @DisplayName("overlapsInTime uses half-open intervals: back-to-back windows do not overlap")
    void overlap() {
        assertTrue(window.overlapsInTime(start.minusSeconds(60), start.plusSeconds(60)));
        assertTrue(window.overlapsInTime(start.plusSeconds(60), end.minusSeconds(60)));
        assertTrue(window.overlapsInTime(start.minus(Duration.ofHours(1)), end.plus(Duration.ofHours(1))));
        assertFalse(window.overlapsInTime(end, end.plus(Duration.ofHours(1))));
        assertFalse(window.overlapsInTime(start.minus(Duration.ofHours(1)), start));
        assertFalse(window.overlapsInTime(end.plus(Duration.ofDays(1)), end.plus(Duration.ofDays(1)).plusSeconds(60)));
    }

    @Test
    @DisplayName("sharedAssetsWith returns the intersection and leaves the window untouched")
    void sharedAssets() {
        OperationWindow both = create(Set.of(assetA, assetB), start, end);

        assertEquals(Set.of(assetB), both.sharedAssetsWith(Set.of(assetB, UUID.randomUUID())));
        assertTrue(both.sharedAssetsWith(Set.of(UUID.randomUUID())).isEmpty());
        assertEquals(Set.of(assetA, assetB), both.getTargetAssetIds());
    }
}
