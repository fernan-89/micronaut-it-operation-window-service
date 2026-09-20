package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationWindowEntityTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private OperationWindowEntity entity(UUID id, UUID org, String title, WindowType type, Instant start, Instant end,
                                         WindowStatus status, Instant created, List<UUID> assets) {
        return new OperationWindowEntity(id, org, title, "d", type, assets, start, end, status, null, "planner", created, created, 7L);
    }

    @Test
    @DisplayName("fromDomain / toDomain round-trips the aggregate; a new entity has no @Version yet")
    void roundTrip() {
        UUID asset = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        Instant start = Instant.now().plus(Duration.ofDays(1));
        OperationWindow window = OperationWindow.createNew(UUID.randomUUID(), UUID.randomUUID(), "Firmware", "d", WindowType.PATCHING,
                Set.of(asset), start, start.plus(Duration.ofHours(1)), ticket, "planner");
        window.start();

        OperationWindowEntity entity = OperationWindowEntity.fromDomain(window);
        OperationWindow restored = entity.toDomain();

        assertNull(entity.version());
        assertEquals(List.of(asset), entity.targetAssetIds());
        assertEquals(window.getId(), restored.getId());
        assertEquals(window.getOrganisationId(), restored.getOrganisationId());
        assertEquals("Firmware", restored.getTitle());
        assertEquals(WindowType.PATCHING, restored.getWindowType());
        assertEquals(Set.of(asset), restored.getTargetAssetIds());
        assertEquals(window.getStartAt(), restored.getStartAt());
        assertEquals(window.getEndAt(), restored.getEndAt());
        assertEquals(WindowStatus.IN_PROGRESS, restored.getStatus());
        assertEquals(ticket, restored.getMaintenanceTicketId());
        assertEquals("planner", restored.getRequestedBy());
        assertEquals(window.getCreatedAt(), restored.getCreatedAt());
    }

    @Test
    @DisplayName("fromDomain rejects a null aggregate")
    void fromDomainNull() {
        assertThrows(NullPointerException.class, () -> OperationWindowEntity.fromDomain(null));
    }

    @Test
    @DisplayName("the canonical constructor enforces every persistence invariant")
    void invariants() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        List<UUID> assets = List.of(UUID.randomUUID());
        Instant end = NOW.plusSeconds(3600);
        assertThrows(NullPointerException.class, () -> entity(null, org, "t", WindowType.PATCHING, NOW, end, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, null, "t", WindowType.PATCHING, NOW, end, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, null, WindowType.PATCHING, NOW, end, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, "t", null, NOW, end, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, "t", WindowType.PATCHING, null, end, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, "t", WindowType.PATCHING, NOW, null, WindowStatus.SCHEDULED, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, "t", WindowType.PATCHING, NOW, end, null, NOW, assets));
        assertThrows(NullPointerException.class, () -> entity(id, org, "t", WindowType.PATCHING, NOW, end, WindowStatus.SCHEDULED, null, assets));
        assertThrows(IllegalArgumentException.class, () -> entity(id, org, "  ", WindowType.PATCHING, NOW, end, WindowStatus.SCHEDULED, NOW, assets));
    }

    @Test
    @DisplayName("a null asset list is normalised to an immutable empty list")
    void nullAssets() {
        OperationWindowEntity entity = entity(UUID.randomUUID(), UUID.randomUUID(), "t", WindowType.PATCHING, NOW, NOW.plusSeconds(3600),
                WindowStatus.SCHEDULED, NOW, null);

        assertTrue(entity.targetAssetIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> entity.targetAssetIds().add(UUID.randomUUID()));
    }

    @Test
    @DisplayName("withSchedule only changes the interval and refreshes updatedAt; withStatus only the status")
    void copies() {
        OperationWindowEntity base = entity(UUID.randomUUID(), UUID.randomUUID(), "t", WindowType.PATCHING, NOW, NOW.plusSeconds(3600),
                WindowStatus.SCHEDULED, NOW, List.of(UUID.randomUUID()));

        OperationWindowEntity moved = base.withSchedule(NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        OperationWindowEntity started = base.withStatus(WindowStatus.IN_PROGRESS);

        assertEquals(NOW.plusSeconds(7200), moved.startAt());
        assertEquals(NOW.plusSeconds(10800), moved.endAt());
        assertEquals(WindowStatus.SCHEDULED, moved.status());
        assertEquals(base.version(), moved.version());
        assertTrue(moved.updatedAt().isAfter(base.updatedAt()));

        assertEquals(WindowStatus.IN_PROGRESS, started.status());
        assertEquals(base.startAt(), started.startAt());
        assertEquals(base.targetAssetIds(), started.targetAssetIds());
        assertEquals(base.version(), started.version());
        assertTrue(started.updatedAt().isAfter(base.updatedAt()));
    }
}
