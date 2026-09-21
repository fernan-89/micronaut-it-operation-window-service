package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.response.CollisionEvaluationResponse;
import com.thinklab.application.dto.response.CollisionEvaluationResponse.ConflictResponse;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;

import java.util.List;
import java.util.UUID;

/**
 * Static factory mapper for Operation Window DTOs and Domain Entities. Enforces the DTO Isolation Pattern.
 */
public final class OperationWindowMapper {

    private OperationWindowMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static OperationWindow toDomain(InitiateOperationWindowRequest request, UUID sovereignId,
                                           UUID organisationId, String executor) {
        return OperationWindow.createNew(sovereignId, organisationId, request.title(), request.description(),
                request.windowType(), request.targetAssetIds(), request.startAt(), request.endAt(),
                request.maintenanceTicketId(), executor);
    }

    public static OperationWindowResponse toResponse(OperationWindow window) {
        return new OperationWindowResponse(
                window.getId(),
                window.getOrganisationId(),
                window.getTitle(),
                window.getDescription(),
                window.getWindowType().name(),
                window.getTargetAssetIds(),
                window.getStartAt(),
                window.getEndAt(),
                window.getStatus().name(),
                window.getMaintenanceTicketId(),
                window.getRequestedBy(),
                window.getCreatedAt(),
                window.getUpdatedAt()
        );
    }

    public static CollisionEvaluationResponse toEvaluation(List<WindowConflict> conflicts) {
        return new CollisionEvaluationResponse(
                !conflicts.isEmpty(),
                conflicts.stream()
                        .map(c -> new ConflictResponse(c.windowId(), c.title(), c.startAt(), c.endAt(), c.overlappingAssetIds()))
                        .toList()
        );
    }
}
