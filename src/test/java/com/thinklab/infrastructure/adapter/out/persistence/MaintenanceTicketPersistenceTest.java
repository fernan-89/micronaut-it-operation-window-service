package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.mapper.MaintenanceTicketMapper;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.model.MaintenanceTicket;
import com.thinklab.domain.model.MaintenanceTicket.Comment;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.MaintenanceTicketEntity;
import com.thinklab.infrastructure.adapter.out.persistence.entity.MaintenanceTicketEntity.CommentEntity;
import com.thinklab.infrastructure.adapter.out.persistence.repository.MaintenanceTicketMongoRepository;
import com.thinklab.infrastructure.adapter.out.persistence.repository.MaintenanceTicketRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the MaintenanceTicket mapper, entity and Micronaut Data adapter. */
@ExtendWith(MockitoExtension.class)
class MaintenanceTicketPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Mock private MaintenanceTicketMongoRepository repository;

    private MaintenanceTicketRepositoryAdapter adapter;
    private UUID organisationId;
    private UUID assetId;
    private MaintenanceTicket ticket;
    private MaintenanceTicketEntity entity;

    @BeforeEach
    void setUp() {
        adapter = new MaintenanceTicketRepositoryAdapter(repository);
        organisationId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        ticket = MaintenanceTicket.createNew(UUID.randomUUID(), organisationId, assetId, "Fan noise", "loud");
        entity = MaintenanceTicketEntity.fromDomain(ticket);
    }

    // ------------------------------------------------------------ mapper

    @Test
    @DisplayName("mapper: toDomain builds an OPEN ticket and toResponse projects comments")
    void mapper() {
        UUID id = UUID.randomUUID();
        MaintenanceTicket built = MaintenanceTicketMapper.toDomain(new InitiateMaintenanceTicketRequest(assetId, "T", "D"), organisationId, id);
        built.addComment(new Comment(UUID.randomUUID(), "tech", "looked", NOW), Optional.of(TicketStatus.IN_ANALYSIS));

        MaintenanceTicketResponse response = MaintenanceTicketMapper.toResponse(built);

        assertEquals(id, response.id());
        assertEquals(assetId, response.assetId());
        assertEquals("IN_ANALYSIS", response.status());
        assertEquals(1, response.comments().size());
        assertEquals("tech", response.comments().get(0).author());
        assertEquals("looked", response.comments().get(0).text());
    }

    // ------------------------------------------------------------ entity

    @Test
    @DisplayName("entity: round-trips the aggregate including comments; a new entity has no @Version")
    void entityRoundTrip() {
        ticket.addComment(new Comment(UUID.randomUUID(), "tech", "checked", NOW), Optional.of(TicketStatus.IN_ANALYSIS));

        MaintenanceTicketEntity mapped = MaintenanceTicketEntity.fromDomain(ticket);
        MaintenanceTicket restored = mapped.toDomain();

        assertNull(mapped.version());
        assertEquals(1, mapped.comments().size());
        assertEquals(ticket.getId(), restored.getId());
        assertEquals(TicketStatus.IN_ANALYSIS, restored.getStatus());
        assertEquals("checked", restored.getComments().get(0).text());
        assertEquals(assetId, restored.getAssetId());
    }

    @Test
    @DisplayName("entity: toDomain tolerates a null comment list; the constructor enforces invariants")
    void entityInvariants() {
        MaintenanceTicketEntity noComments = new MaintenanceTicketEntity(UUID.randomUUID(), organisationId, assetId, "t", null,
                TicketStatus.OPEN, null, NOW, NOW, null);
        assertEquals(0, noComments.toDomain().getComments().size());

        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(null, organisationId, assetId, "t", null, TicketStatus.OPEN, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(id, null, assetId, "t", null, TicketStatus.OPEN, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(id, organisationId, null, "t", null, TicketStatus.OPEN, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(id, organisationId, assetId, null, null, TicketStatus.OPEN, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(id, organisationId, assetId, "t", null, null, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketEntity(id, organisationId, assetId, "t", null, TicketStatus.OPEN, null, null, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new MaintenanceTicketEntity(id, organisationId, assetId, " ", null, TicketStatus.OPEN, null, NOW, NOW, null));
        assertThrows(NullPointerException.class, () -> MaintenanceTicketEntity.fromDomain(null));
    }

    // ------------------------------------------------------------ adapter

    @Test
    @DisplayName("adapter: the constructor rejects a null repository")
    void adapterConstructor() {
        assertThrows(NullPointerException.class, () -> new MaintenanceTicketRepositoryAdapter(null));
    }

    @Test
    @DisplayName("adapter: create saves the mapped entity and maps the result back")
    void create() {
        when(repository.save(any(MaintenanceTicketEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.create(ticket))
                .expectNextMatches(saved -> saved.getId().equals(ticket.getId()) && saved.getStatus() == TicketStatus.OPEN)
                .verifyComplete();
    }

    @Test
    @DisplayName("adapter: findById maps the entity, or completes empty")
    void findById() {
        when(repository.findById(ticket.getId())).thenReturn(Mono.just(entity)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(ticket.getId())).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findById(ticket.getId())).verifyComplete();
    }

    @Test
    @DisplayName("adapter: findAllByOrganisationId uses the status query only when a status is given")
    void findAll() {
        when(repository.findByOrganisationIdAndStatus(organisationId, TicketStatus.OPEN)).thenReturn(Flux.just(entity));
        when(repository.findByOrganisationId(organisationId)).thenReturn(Flux.just(entity, entity));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, TicketStatus.OPEN)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).expectNextCount(2).verifyComplete();
    }

    @Test
    @DisplayName("adapter: updateStatus rewrites only the status")
    void updateStatus() {
        when(repository.findById(ticket.getId())).thenReturn(Mono.just(entity));
        when(repository.update(any(MaintenanceTicketEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.updateStatus(ticket.getId(), TicketStatus.IN_ANALYSIS)).verifyComplete();

        ArgumentCaptor<MaintenanceTicketEntity> captor = ArgumentCaptor.forClass(MaintenanceTicketEntity.class);
        verify(repository).update(captor.capture());
        assertEquals(TicketStatus.IN_ANALYSIS, captor.getValue().status());
        assertEquals("Fan noise", captor.getValue().title());
    }

    @Test
    @DisplayName("adapter: appendComment adds the comment and applies the resulting status in one write")
    void appendComment() {
        when(repository.findById(ticket.getId())).thenReturn(Mono.just(entity));
        when(repository.update(any(MaintenanceTicketEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Comment comment = new Comment(UUID.randomUUID(), "tech", "swapped fan", NOW);

        StepVerifier.create(adapter.appendComment(ticket.getId(), comment, TicketStatus.OPEN)).verifyComplete();

        ArgumentCaptor<MaintenanceTicketEntity> captor = ArgumentCaptor.forClass(MaintenanceTicketEntity.class);
        verify(repository).update(captor.capture());
        List<CommentEntity> comments = captor.getValue().comments();
        assertEquals(1, comments.size());
        assertEquals("swapped fan", comments.get(0).text());
        assertEquals(TicketStatus.OPEN, captor.getValue().status());
    }

    @Test
    @DisplayName("adapter: writes fail with MaintenanceTicketNotFoundException when the ticket is missing")
    void writesFailWhenMissing() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.updateStatus(missing, TicketStatus.IN_ANALYSIS)).expectError(MaintenanceTicketNotFoundException.class).verify();
        StepVerifier.create(adapter.appendComment(missing, new Comment(UUID.randomUUID(), "a", "t", NOW), TicketStatus.OPEN))
                .expectError(MaintenanceTicketNotFoundException.class).verify();

        verify(repository, never()).update(any(MaintenanceTicketEntity.class));
    }
}
