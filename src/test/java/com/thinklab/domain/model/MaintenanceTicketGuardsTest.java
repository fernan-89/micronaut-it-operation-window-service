package com.thinklab.domain.model;

import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceTicketGuardsTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    @DisplayName("createNew and reconstitute require identifiers and a title")
    void factoryGuards() {
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.createNew(null, ID, ID, "t", "d"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.createNew(ID, null, ID, "t", "d"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.createNew(ID, ID, null, "t", "d"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.createNew(ID, ID, ID, null, "d"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.createNew(ID, ID, ID, " ", "d"));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.reconstitute(null, ID, ID, "t", "d", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.reconstitute(ID, null, ID, "t", "d", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.reconstitute(ID, ID, null, "t", "d", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> MaintenanceTicket.reconstitute(ID, ID, ID, null, "d", null, null, null, null));
    }

    @Test
    @DisplayName("reconstitute defaults a missing status, comments and timestamps and keeps explicit ones")
    void reconstituteDefaults() {
        MaintenanceTicket defaulted = MaintenanceTicket.reconstitute(ID, ID, ID, "t", "d", null, null, null, null);

        assertEquals(TicketStatus.OPEN, defaulted.getStatus());
        assertTrue(defaulted.getComments().isEmpty());
        assertNotNull(defaulted.getCreatedAt());
        assertEquals(defaulted.getCreatedAt(), defaulted.getUpdatedAt());

        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");
        Comment comment = new Comment(UUID.randomUUID(), "tech", "note", created);
        MaintenanceTicket explicit = MaintenanceTicket.reconstitute(ID, ID, ID, "t", "d", TicketStatus.IN_ANALYSIS,
                List.of(comment), created, updated);
        assertEquals(TicketStatus.IN_ANALYSIS, explicit.getStatus());
        assertEquals(1, explicit.getComments().size());
        assertEquals(created, explicit.getCreatedAt());
        assertEquals(updated, explicit.getUpdatedAt());
    }

    @Test
    @DisplayName("addComment rejects a null comment")
    void nullComment() {
        MaintenanceTicket ticket = MaintenanceTicket.createNew(ID, ID, ID, "t", "d");

        assertThrows(IllegalArgumentException.class, () -> ticket.addComment(null, Optional.empty()));
    }

    @Test
    @DisplayName("ticket status transition matrix")
    void statusMatrix() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_ANALYSIS));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.COMPLETED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.AWAITING_PARTS));
        assertTrue(TicketStatus.IN_ANALYSIS.canTransitionTo(TicketStatus.AWAITING_PARTS));
        assertTrue(TicketStatus.IN_ANALYSIS.canTransitionTo(TicketStatus.COMPLETED));
        assertFalse(TicketStatus.IN_ANALYSIS.canTransitionTo(TicketStatus.OPEN));
        assertTrue(TicketStatus.AWAITING_PARTS.canTransitionTo(TicketStatus.IN_ANALYSIS));
        assertFalse(TicketStatus.AWAITING_PARTS.canTransitionTo(TicketStatus.COMPLETED));
        for (TicketStatus target : TicketStatus.values()) {
            assertFalse(TicketStatus.COMPLETED.canTransitionTo(target));
        }
        assertFalse(TicketStatus.OPEN.canTransitionTo(null));
        assertThrows(NullPointerException.class, () -> TicketStatus.OPEN.validateTransitionTo(null));
    }
}
