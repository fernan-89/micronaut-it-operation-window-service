package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for MaintenanceTicket Creation Request (BIAN Behavior Qualifier: {@code initiate}).
 * Acts as a protective barrier to the Domain Layer.
 */
@Serdeable
public record InitiateMaintenanceTicketRequest(

        @NotNull(message = "Asset ID is required")
        UUID assetId,

        @NotBlank(message = "Title is required")
        String title,

        String description
) {}
