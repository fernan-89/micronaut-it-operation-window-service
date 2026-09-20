package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.response.CollisionEvaluationResponse;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowType;
import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationWindowMapperTest {

    private final Instant start = Instant.now().plus(Duration.ofDays(1));
    private final Instant end = start.plus(Duration.ofHours(1));

    @Test
    @DisplayName("toDomain builds a SCHEDULED aggregate from the request, sovereign ID, tenant and executor")
    void toDomain() {
        UUID id = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        InitiateOperationWindowRequest request = new InitiateOperationWindowRequest("Title", "Desc", WindowType.DEPLOYMENT,
                Set.of(asset), start, end, ticket);

        OperationWindow window = OperationWindowMapper.toDomain(request, id, org, "planner");

        assertEquals(id, window.getId());
        assertEquals(org, window.getOrganisationId());
        assertEquals("Title", window.getTitle());
        assertEquals(WindowType.DEPLOYMENT, window.getWindowType());
        assertEquals(Set.of(asset), window.getTargetAssetIds());
        assertEquals(ticket, window.getMaintenanceTicketId());
        assertEquals("planner", window.getRequestedBy());
    }

    @Test
    @DisplayName("toResponse flattens enums to names and copies every field")
    void toResponse() {
        UUID asset = UUID.randomUUID();
        OperationWindow window = OperationWindow.createNew(UUID.randomUUID(), UUID.randomUUID(), "T", "D", WindowType.MIGRATION,
                Set.of(asset), start, end, null, "planner");
        window.start();

        OperationWindowResponse response = OperationWindowMapper.toResponse(window);

        assertEquals(window.getId(), response.id());
        assertEquals("MIGRATION", response.windowType());
        assertEquals("IN_PROGRESS", response.status());
        assertEquals(Set.of(asset), response.targetAssetIds());
        assertEquals(start, response.startAt());
        assertEquals(end, response.endAt());
        assertEquals("planner", response.requestedBy());
    }

    @Test
    @DisplayName("toEvaluation maps conflicts and derives the collides flag")
    void toEvaluation() {
        UUID asset = UUID.randomUUID();
        WindowConflict conflict = new WindowConflict(UUID.randomUUID(), "Other", start, end, Set.of(asset));

        CollisionEvaluationResponse withConflict = OperationWindowMapper.toEvaluation(List.of(conflict));
        CollisionEvaluationResponse free = OperationWindowMapper.toEvaluation(List.of());

        assertTrue(withConflict.collides());
        assertEquals(conflict.windowId(), withConflict.conflicts().get(0).windowId());
        assertEquals(Set.of(asset), withConflict.conflicts().get(0).overlappingAssetIds());
        assertFalse(free.collides());
        assertTrue(free.conflicts().isEmpty());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<OperationWindowMapper> constructor = OperationWindowMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
