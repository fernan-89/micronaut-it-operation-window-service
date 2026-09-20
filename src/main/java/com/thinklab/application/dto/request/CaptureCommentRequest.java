package com.thinklab.application.dto.request;

import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for capturing a forensic comment on a MaintenanceTicket (BIAN Behavior Qualifier:
 * {@code comment/initiate}). {@code targetStatus} is optional — when present, the comment
 * drives a validated FSM transition in the same operation.
 */
@Serdeable
public record CaptureCommentRequest(

        @NotBlank(message = "Comment text is required")
        String text,

        @Nullable
        TicketStatus targetStatus
) {}
