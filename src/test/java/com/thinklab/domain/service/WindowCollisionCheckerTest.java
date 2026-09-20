package com.thinklab.domain.service;

import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowCollisionCheckerTest {

    private UUID org;
    private UUID assetA;
    private UUID assetB;
    private Instant base;

    @BeforeEach
    void setUp() {
        org = UUID.randomUUID();
        assetA = UUID.randomUUID();
        assetB = UUID.randomUUID();
        base = Instant.now().plus(Duration.ofDays(1));
    }

    private OperationWindow window(String title, Set<UUID> assets, long fromHours, long toHours) {
        return OperationWindow.createNew(UUID.randomUUID(), org, title, null, WindowType.MAINTENANCE, assets,
                base.plus(Duration.ofHours(fromHours)), base.plus(Duration.ofHours(toHours)), null, "planner");
    }

    private List<WindowConflict> detect(Set<UUID> assets, long fromHours, long toHours, OperationWindow... existing) {
        return WindowCollisionChecker.detect(null, assets, base.plus(Duration.ofHours(fromHours)),
                base.plus(Duration.ofHours(toHours)), List.of(existing));
    }

    @Test
    @DisplayName("no existing windows means no conflicts")
    void empty() {
        assertTrue(detect(Set.of(assetA), 0, 2).isEmpty());
    }

    @Test
    @DisplayName("an overlapping window on the same asset is a conflict carrying the impact")
    void overlapSameAsset() {
        OperationWindow existing = window("Firmware", Set.of(assetA, assetB), 1, 3);

        List<WindowConflict> conflicts = detect(Set.of(assetA), 0, 2, existing);

        assertEquals(1, conflicts.size());
        WindowConflict conflict = conflicts.get(0);
        assertEquals(existing.getId(), conflict.windowId());
        assertEquals("Firmware", conflict.title());
        assertEquals(existing.getStartAt(), conflict.startAt());
        assertEquals(existing.getEndAt(), conflict.endAt());
        assertEquals(Set.of(assetA), conflict.overlappingAssetIds());
    }

    @Test
    @DisplayName("the impact lists only the assets actually shared, not the whole target set")
    void impactIsTheIntersection() {
        OperationWindow existing = window("Rack move", Set.of(assetA, assetB, UUID.randomUUID()), 0, 4);

        List<WindowConflict> conflicts = detect(Set.of(assetB, UUID.randomUUID()), 1, 2, existing);

        assertEquals(Set.of(assetB), conflicts.get(0).overlappingAssetIds());
    }

    @Test
    @DisplayName("overlap in time on a different asset is not a conflict")
    void differentAsset() {
        assertTrue(detect(Set.of(assetB), 0, 2, window("Other", Set.of(assetA), 0, 2)).isEmpty());
    }

    @Test
    @DisplayName("same asset without a time overlap is not a conflict, including back-to-back windows")
    void noTimeOverlap() {
        assertTrue(detect(Set.of(assetA), 4, 6, window("Later", Set.of(assetA), 0, 2)).isEmpty());
        assertTrue(detect(Set.of(assetA), 2, 4, window("Back-to-back", Set.of(assetA), 0, 2)).isEmpty());
        assertTrue(detect(Set.of(assetA), 0, 2, window("Back-to-back reversed", Set.of(assetA), 2, 4)).isEmpty());
    }

    @Test
    @DisplayName("containment in either direction is a conflict")
    void containment() {
        assertEquals(1, detect(Set.of(assetA), 1, 2, window("Big", Set.of(assetA), 0, 10)).size());
        assertEquals(1, detect(Set.of(assetA), 0, 10, window("Small", Set.of(assetA), 4, 5)).size());
    }

    @Test
    @DisplayName("only ACTIVE windows block: COMPLETED and CANCELLED are ignored, IN_PROGRESS still blocks")
    void inactiveWindowsAreIgnored() {
        OperationWindow completed = window("Done", Set.of(assetA), 0, 2);
        completed.start();
        completed.complete();
        OperationWindow cancelled = window("Cancelled", Set.of(assetA), 0, 2);
        cancelled.cancel();
        OperationWindow running = window("Running", Set.of(assetA), 0, 2);
        running.start();

        assertTrue(detect(Set.of(assetA), 0, 2, completed, cancelled).isEmpty());
        assertEquals(1, detect(Set.of(assetA), 0, 2, completed, cancelled, running).size());
        assertEquals(WindowStatus.IN_PROGRESS, running.getStatus());
    }

    @Test
    @DisplayName("the window being rescheduled is excluded from its own check")
    void excludesCandidate() {
        OperationWindow self = window("Self", Set.of(assetA), 0, 2);
        OperationWindow other = window("Other", Set.of(assetA), 1, 3);

        List<WindowConflict> conflicts = WindowCollisionChecker.detect(self.getId(), Set.of(assetA),
                base, base.plus(Duration.ofHours(2)), List.of(self, other));

        assertEquals(1, conflicts.size());
        assertEquals(other.getId(), conflicts.get(0).windowId());
    }

    @Test
    @DisplayName("conflicts are ordered by start time, then id")
    void ordering() {
        OperationWindow late = window("Late", Set.of(assetA), 3, 5);
        OperationWindow early = window("Early", Set.of(assetA), 0, 2);
        OperationWindow middle = window("Middle", Set.of(assetA), 1, 4);

        List<WindowConflict> conflicts = detect(Set.of(assetA), 0, 6, late, early, middle);

        assertEquals(List.of("Early", "Middle", "Late"), conflicts.stream().map(WindowConflict::title).toList());
    }

    @Test
    @DisplayName("invalid candidates are rejected before any comparison")
    void invalidCandidate() {
        assertThrows(IllegalArgumentException.class, () -> WindowCollisionChecker.detect(null, Set.of(), base, base.plusSeconds(3600), List.of()));
        assertThrows(IllegalArgumentException.class, () -> WindowCollisionChecker.detect(null, Set.of(assetA), base, base, List.of()));
        assertThrows(IllegalArgumentException.class, () -> WindowCollisionChecker.detect(null, Set.of(assetA), null, base, List.of()));
    }

    @Test
    @DisplayName("the checker is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<WindowCollisionChecker> constructor = WindowCollisionChecker.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
