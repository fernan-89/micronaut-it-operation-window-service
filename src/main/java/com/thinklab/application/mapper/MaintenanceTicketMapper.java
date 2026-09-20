package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.dto.response.MaintenanceTicketResponse.CommentResponse;
import com.thinklab.domain.model.MaintenanceTicket;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Static mapper bridging the Application DTOs and the pure Domain Model.
 * Strictly stateless. Framework-agnostic.
 */
public final class MaintenanceTicketMapper {

    private MaintenanceTicketMapper() { throw new UnsupportedOperationException(); }

    public static MaintenanceTicket toDomain(InitiateMaintenanceTicketRequest request, UUID organisationId, UUID sovereignId) {
        return MaintenanceTicket.createNew(sovereignId, organisationId, request.assetId(), request.title(), request.description());
    }

    public static MaintenanceTicketResponse toResponse(MaintenanceTicket ticket) {
        List<CommentResponse> comments = ticket.getComments().stream()
                .map(c -> new CommentResponse(c.commentId(), c.author(), c.text(), c.createdAt()))
                .collect(Collectors.toList());

        return new MaintenanceTicketResponse(
                ticket.getId(),
                ticket.getOrganisationId(),
                ticket.getAssetId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                comments,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
