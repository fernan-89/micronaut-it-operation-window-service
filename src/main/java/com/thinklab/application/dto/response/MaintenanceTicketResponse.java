package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for MaintenanceTicket Output Payload (IT Operation Window Control Record).
 * Enforces the DTO Isolation Pattern by preventing the pure Domain Model
 * from bleeding out into the HTTP/External boundaries.
 */
@Serdeable
public record MaintenanceTicketResponse(
        UUID id,
        UUID organisationId,
        UUID assetId,
        String title,
        String description,
        String status,
        List<CommentResponse> comments,
        Instant createdAt,
        Instant updatedAt
) {
    @Serdeable
    public record CommentResponse(
            UUID commentId,
            String author,
            String text,
            Instant createdAt
    ) {}
}
