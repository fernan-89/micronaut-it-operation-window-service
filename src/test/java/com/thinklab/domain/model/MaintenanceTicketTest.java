package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidTicketStatusException;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceTicketTest {

    private MaintenanceTicket newTicket() {
        return MaintenanceTicket.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Broken fan", "Server fan making noise");
    }

    @Test
    @DisplayName("Should create a new ticket in OPEN state")
    void shouldCreateInOpenState() {
        MaintenanceTicket ticket = newTicket();

        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertTrue(ticket.getComments().isEmpty());
    }

    @Test
    @DisplayName("Should follow the legal FSM path OPEN -> IN_ANALYSIS -> AWAITING_PARTS -> IN_ANALYSIS -> COMPLETED")
    void shouldFollowLegalFsmPath() {
        MaintenanceTicket ticket = newTicket();

        ticket.changeStatus(TicketStatus.IN_ANALYSIS);
        assertEquals(TicketStatus.IN_ANALYSIS, ticket.getStatus());

        ticket.changeStatus(TicketStatus.AWAITING_PARTS);
        assertEquals(TicketStatus.AWAITING_PARTS, ticket.getStatus());

        ticket.changeStatus(TicketStatus.IN_ANALYSIS);
        assertEquals(TicketStatus.IN_ANALYSIS, ticket.getStatus());

        ticket.changeStatus(TicketStatus.COMPLETED);
        assertEquals(TicketStatus.COMPLETED, ticket.getStatus());
    }

    @Test
    @DisplayName("Should reject transitioning directly from OPEN to COMPLETED")
    void shouldRejectOpenToCompleted() {
        MaintenanceTicket ticket = newTicket();

        assertThrows(InvalidTicketStatusException.class, () -> ticket.changeStatus(TicketStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should reject any transition out of terminal COMPLETED")
    void shouldRejectTransitionOutOfCompleted() {
        MaintenanceTicket ticket = newTicket();
        ticket.changeStatus(TicketStatus.IN_ANALYSIS);
        ticket.changeStatus(TicketStatus.COMPLETED);

        assertThrows(InvalidTicketStatusException.class, () -> ticket.changeStatus(TicketStatus.IN_ANALYSIS));
    }

    @Test
    @DisplayName("Should reject redundant self-transitions")
    void shouldRejectSelfTransition() {
        MaintenanceTicket ticket = newTicket();

        assertThrows(InvalidTicketStatusException.class, () -> ticket.changeStatus(TicketStatus.OPEN));
    }

    @Test
    @DisplayName("Should append a comment without changing status when no targetStatus is given")
    void shouldAppendCommentWithoutTransition() {
        MaintenanceTicket ticket = newTicket();
        Comment comment = new Comment(UUID.randomUUID(), "tech-01", "Investigating", Instant.now());

        ticket.addComment(comment, Optional.empty());

        assertEquals(1, ticket.getComments().size());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
    }

    @Test
    @DisplayName("Should advance the FSM when a comment carries a targetStatus")
    void shouldAdvanceFsmViaComment() {
        MaintenanceTicket ticket = newTicket();
        Comment comment = new Comment(UUID.randomUUID(), "tech-01", "Starting analysis", Instant.now());

        ticket.addComment(comment, Optional.of(TicketStatus.IN_ANALYSIS));

        assertEquals(1, ticket.getComments().size());
        assertEquals(TicketStatus.IN_ANALYSIS, ticket.getStatus());
    }

    @Test
    @DisplayName("Should reject an illegal targetStatus carried by a comment, same as a direct control call")
    void shouldRejectIllegalCommentDrivenTransition() {
        MaintenanceTicket ticket = newTicket();
        Comment comment = new Comment(UUID.randomUUID(), "tech-01", "Trying to skip ahead", Instant.now());

        assertThrows(InvalidTicketStatusException.class, () -> ticket.addComment(comment, Optional.of(TicketStatus.COMPLETED)));
    }
}
