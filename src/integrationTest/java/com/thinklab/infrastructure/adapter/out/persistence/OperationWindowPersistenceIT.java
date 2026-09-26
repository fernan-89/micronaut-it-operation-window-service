package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import com.thinklab.domain.repository.MaintenanceTicketRepository;
import com.thinklab.domain.repository.OperationWindowRepository;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operation windows and maintenance tickets against a real MongoDB, through the Micronaut Data queries the
 * unit suite can only mock — above all the collision query behind ADR-018: half-open intervals, only active
 * windows, only the same tenant.
 *
 * <p>{@code packages = "com.thinklab"}: otherwise Micronaut Data MongoDB stops mapping {@code @Id} to
 * {@code _id} for entities outside the test's own package (see party-authentication's integration tests).
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationWindowPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_operation_window_it";
    private static final Instant T10 = Instant.parse("2026-10-01T10:00:00Z");

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    OperationWindowRepository windows;

    @Inject
    MaintenanceTicketRepository tickets;

    @Inject
    MongoClient mongoClient;

    private Set<Document> indexKeys(String collection) {
        return Flux.from(mongoClient.getDatabase(DATABASE).getCollection(collection).listIndexes())
                .map(index -> index.get("key", Document.class)).collect(Collectors.toSet()).block();
    }

    private static Instant at(int hour) {
        return T10.plus(hour - 10L, ChronoUnit.HOURS);
    }

    private OperationWindow window(UUID organisation, int fromHour, int toHour, UUID... assets) {
        return windows.create(OperationWindow.createNew(UUID.randomUUID(), organisation, "Patch Tuesday", "monthly patching",
                WindowType.PATCHING, assets.length > 0 ? Set.of(assets) : Set.of(UUID.randomUUID()), at(fromHour), at(toHour), null, "it-operator")).block();
    }

    private Set<UUID> overlapping(UUID organisation, int fromHour, int toHour) {
        return windows.findActiveOverlapping(organisation, at(fromHour), at(toHour))
                .map(OperationWindow::getId).collect(Collectors.toSet()).block();
    }

    @Test
    @DisplayName("collision query: half-open intervals, so windows that only touch do not collide")
    void overlapIsHalfOpen() {
        UUID organisation = UUID.randomUUID();
        OperationWindow tenToTwelve = window(organisation, 10, 12);

        assertEquals(Set.of(tenToTwelve.getId()), overlapping(organisation, 11, 13));
        assertEquals(Set.of(tenToTwelve.getId()), overlapping(organisation, 9, 11));
        assertEquals(Set.of(tenToTwelve.getId()), overlapping(organisation, 10, 12));
        assertEquals(Set.of(tenToTwelve.getId()), overlapping(organisation, 9, 13));
        assertEquals(Set.of(), overlapping(organisation, 12, 13));
        assertEquals(Set.of(), overlapping(organisation, 8, 10));
    }

    @Test
    @DisplayName("collision query: only SCHEDULED and IN_PROGRESS windows of the same tenant count")
    void overlapCountsOnlyActiveWindowsOfTheTenant() {
        UUID organisation = UUID.randomUUID();
        OperationWindow scheduled = window(organisation, 10, 12);
        OperationWindow inProgress = window(organisation, 10, 12);
        OperationWindow completed = window(organisation, 10, 12);
        OperationWindow cancelled = window(organisation, 10, 12);
        window(UUID.randomUUID(), 10, 12);
        windows.updateStatus(inProgress.getId(), WindowStatus.IN_PROGRESS).block();
        windows.updateStatus(completed.getId(), WindowStatus.IN_PROGRESS).block();
        windows.updateStatus(completed.getId(), WindowStatus.COMPLETED).block();
        windows.updateStatus(cancelled.getId(), WindowStatus.CANCELLED).block();

        assertEquals(Set.of(scheduled.getId(), inProgress.getId()), overlapping(organisation, 11, 13));
    }

    @Test
    @DisplayName("a rescheduled window collides at its new time, not its old one")
    void reschedule() {
        UUID organisation = UUID.randomUUID();
        OperationWindow moved = window(organisation, 10, 12);

        windows.updateSchedule(moved.getId(), at(14), at(16)).block();

        OperationWindow found = windows.findById(moved.getId()).block();
        assertEquals(at(14), found.getStartAt());
        assertEquals(at(16), found.getEndAt());
        assertEquals(Set.of(), overlapping(organisation, 10, 12));
        assertEquals(Set.of(moved.getId()), overlapping(organisation, 15, 17));
    }

    @Test
    @DisplayName("listing is tenant-scoped with optional status and target-asset filters")
    void listing() {
        UUID organisation = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        OperationWindow withAsset = window(organisation, 10, 11, asset);
        OperationWindow other = window(organisation, 12, 13, UUID.randomUUID());
        windows.updateStatus(other.getId(), WindowStatus.CANCELLED).block();

        assertEquals(Set.of(withAsset.getId(), other.getId()), ids(windows.findAllByOrganisationId(organisation, null, null).collectList().block()));
        assertEquals(Set.of(withAsset.getId()), ids(windows.findAllByOrganisationId(organisation, WindowStatus.SCHEDULED, null).collectList().block()));
        assertEquals(Set.of(withAsset.getId()), ids(windows.findAllByOrganisationId(organisation, null, asset).collectList().block()));
        assertEquals(Set.of(asset), windows.findById(withAsset.getId()).block().getTargetAssetIds());
    }

    @Test
    @DisplayName("unknown windows and tickets: empty on read, not-found on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(windows.findById(unknown).block());
        assertThrows(WindowNotFoundException.class, () -> windows.updateStatus(unknown, WindowStatus.CANCELLED).block());
        assertThrows(WindowNotFoundException.class, () -> windows.updateSchedule(unknown, at(1), at(2)).block());
        assertNull(tickets.findById(unknown).block());
        assertThrows(MaintenanceTicketNotFoundException.class, () -> tickets.updateStatus(unknown, TicketStatus.COMPLETED).block());
    }

    @Test
    @DisplayName("maintenance tickets: comments are appended in order with the status change, and status filters the listing")
    void tickets() {
        UUID organisation = UUID.randomUUID();
        MaintenanceTicket ticket = tickets.create(MaintenanceTicket.createNew(UUID.randomUUID(), organisation, UUID.randomUUID(),
                "Disk failure", "RAID degraded")).block();
        MaintenanceTicket untouched = tickets.create(MaintenanceTicket.createNew(UUID.randomUUID(), organisation, UUID.randomUUID(),
                "Fan noise", "rack 4")).block();
        Comment first = new Comment(UUID.randomUUID(), "it-operator", "ordered a replacement", at(10));
        Comment second = new Comment(UUID.randomUUID(), "it-operator", "disk replaced", at(11));

        tickets.appendComment(ticket.getId(), first, TicketStatus.AWAITING_PARTS).block();
        tickets.appendComment(ticket.getId(), second, TicketStatus.IN_ANALYSIS).block();
        tickets.updateStatus(ticket.getId(), TicketStatus.COMPLETED).block();

        MaintenanceTicket found = tickets.findById(ticket.getId()).block();
        assertEquals(List.of(first, second), found.getComments());
        assertEquals(TicketStatus.COMPLETED, found.getStatus());
        assertEquals(Set.of(untouched.getId()),
                tickets.findAllByOrganisationId(organisation, TicketStatus.OPEN).map(MaintenanceTicket::getId).collect(Collectors.toSet()).block());
        assertEquals(2, tickets.findAllByOrganisationId(organisation, null).count().block());
    }

    private static Set<UUID> ids(List<OperationWindow> list) {
        return list.stream().map(OperationWindow::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("the declared indexes exist, and the collision query is served by an index scan, not a collection scan")
    void collisionQueryUsesTheIndex() {
        window(UUID.randomUUID(), 10, 12);
        Document windowsIndex = new Document("organisationId", 1).append("status", 1).append("startAt", 1);

        assertTrue(indexKeys("operation_windows").contains(windowsIndex), () -> "operation_windows: " + indexKeys("operation_windows"));
        assertTrue(indexKeys("maintenance_tickets").contains(new Document("organisationId", 1).append("status", 1)),
                () -> "maintenance_tickets: " + indexKeys("maintenance_tickets"));

        Document plan = Mono.from(mongoClient.getDatabase(DATABASE).getCollection("operation_windows")
                .find(Filters.and(
                        Filters.eq("organisationId", UUID.randomUUID()),
                        Filters.in("status", List.of(WindowStatus.SCHEDULED.name(), WindowStatus.IN_PROGRESS.name())),
                        Filters.lt("startAt", at(13)),
                        Filters.gt("endAt", at(11))))
                .explain(Document.class)).block();
        String winningPlan = plan.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        assertTrue(winningPlan.contains("IXSCAN") && winningPlan.contains("organisationId_1_status_1_startAt_1"), winningPlan);
    }
}
