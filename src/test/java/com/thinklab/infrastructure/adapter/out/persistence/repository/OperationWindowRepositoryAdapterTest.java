package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.model.OperationWindow.WindowType;
import com.thinklab.infrastructure.adapter.out.persistence.entity.OperationWindowEntity;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWindowRepositoryAdapterTest {

    @Mock private OperationWindowMongoRepository repository;

    private OperationWindowRepositoryAdapter adapter;
    private UUID organisationId;
    private UUID assetA;
    private UUID assetB;
    private Instant start;
    private Instant end;
    private OperationWindow window;
    private OperationWindowEntity entityA;
    private OperationWindowEntity entityB;

    @BeforeEach
    void setUp() {
        adapter = new OperationWindowRepositoryAdapter(repository);
        organisationId = UUID.randomUUID();
        assetA = UUID.randomUUID();
        assetB = UUID.randomUUID();
        start = Instant.now().plus(Duration.ofDays(1));
        end = start.plus(Duration.ofHours(1));
        window = OperationWindow.createNew(UUID.randomUUID(), organisationId, "Firmware", null, WindowType.PATCHING,
                Set.of(assetA), start, end, null, "planner");
        entityA = OperationWindowEntity.fromDomain(window);
        entityB = OperationWindowEntity.fromDomain(OperationWindow.createNew(UUID.randomUUID(), organisationId, "Other", null,
                WindowType.MIGRATION, Set.of(assetB), start, end, null, "planner"));
    }

    @Test
    @DisplayName("the constructor rejects a null repository")
    void constructor() {
        assertThrows(NullPointerException.class, () -> new OperationWindowRepositoryAdapter(null));
    }

    @Test
    @DisplayName("create maps the aggregate to an entity, saves it and maps the result back")
    void create() {
        when(repository.save(any(OperationWindowEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.create(window))
                .expectNextMatches(saved -> saved.getId().equals(window.getId()) && saved.getStatus() == WindowStatus.SCHEDULED)
                .verifyComplete();

        ArgumentCaptor<OperationWindowEntity> captor = ArgumentCaptor.forClass(OperationWindowEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(List.of(assetA), captor.getValue().targetAssetIds());
    }

    @Test
    @DisplayName("findById maps the entity, or completes empty")
    void findById() {
        when(repository.findById(window.getId())).thenReturn(Mono.just(entityA)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(window.getId()))
                .expectNextMatches(found -> found.getTitle().equals("Firmware"))
                .verifyComplete();
        StepVerifier.create(adapter.findById(window.getId())).verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId uses the status-scoped query only when a status is given")
    void findAllStatusScoping() {
        when(repository.findByOrganisationIdAndStatus(organisationId, WindowStatus.SCHEDULED)).thenReturn(Flux.just(entityA));
        when(repository.findByOrganisationId(organisationId)).thenReturn(Flux.just(entityA, entityB));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, WindowStatus.SCHEDULED, null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, null)).expectNextCount(2).verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId filters by target asset in memory")
    void findAllAssetFilter() {
        when(repository.findByOrganisationId(organisationId)).thenReturn(Flux.just(entityA, entityB));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, assetB))
                .expectNextMatches(found -> found.getTitle().equals("Other"))
                .verifyComplete();
    }

    @Test
    @DisplayName("findActiveOverlapping asks for SCHEDULED/IN_PROGRESS windows with startAt < end and endAt > start")
    @SuppressWarnings("unchecked")
    void findActiveOverlapping() {
        when(repository.findByOrganisationIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any(), any()))
                .thenReturn(Flux.just(entityA));

        StepVerifier.create(adapter.findActiveOverlapping(organisationId, start, end)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Collection<WindowStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findByOrganisationIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq(organisationId), statuses.capture(), eq(end), eq(start));
        assertEquals(Set.of(WindowStatus.SCHEDULED, WindowStatus.IN_PROGRESS), Set.copyOf(statuses.getValue()));
    }

    @Test
    @DisplayName("updateSchedule rewrites only the interval, keeping status and version")
    void updateSchedule() {
        Instant newStart = start.plus(Duration.ofDays(1));
        Instant newEnd = newStart.plus(Duration.ofHours(2));
        when(repository.findById(window.getId())).thenReturn(Mono.just(entityA));
        when(repository.update(any(OperationWindowEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.updateSchedule(window.getId(), newStart, newEnd)).verifyComplete();

        ArgumentCaptor<OperationWindowEntity> captor = ArgumentCaptor.forClass(OperationWindowEntity.class);
        verify(repository).update(captor.capture());
        assertEquals(newStart, captor.getValue().startAt());
        assertEquals(newEnd, captor.getValue().endAt());
        assertEquals(WindowStatus.SCHEDULED, captor.getValue().status());
        assertTrue(captor.getValue().updatedAt().isAfter(entityA.updatedAt()) || captor.getValue().updatedAt().equals(entityA.updatedAt()));
    }

    @Test
    @DisplayName("updateStatus rewrites only the status")
    void updateStatus() {
        when(repository.findById(window.getId())).thenReturn(Mono.just(entityA));
        when(repository.update(any(OperationWindowEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.updateStatus(window.getId(), WindowStatus.CANCELLED)).verifyComplete();

        ArgumentCaptor<OperationWindowEntity> captor = ArgumentCaptor.forClass(OperationWindowEntity.class);
        verify(repository).update(captor.capture());
        assertEquals(WindowStatus.CANCELLED, captor.getValue().status());
        assertEquals(start, captor.getValue().startAt());
    }

    @Test
    @DisplayName("both updates fail with WindowNotFoundException when the window is missing and never write")
    void updatesFailWhenMissing() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.updateSchedule(missing, start, end)).expectError(WindowNotFoundException.class).verify();
        StepVerifier.create(adapter.updateStatus(missing, WindowStatus.CANCELLED)).expectError(WindowNotFoundException.class).verify();

        verify(repository, never()).update(any(OperationWindowEntity.class));
    }
}
