package com.thinklab.application.dto.request;

import com.thinklab.domain.model.OperationWindow.WindowType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for scheduling a new Operation Window (BIAN Behavior Qualifier: {@code initiate}).
 * organisationId travels via the {@code X-Tenant-Id} header, not the body.
 */
@Serdeable
public record InitiateOperationWindowRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 160, message = "Title must not exceed 160 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @NotNull(message = "Window type is required")
        WindowType windowType,

        @NotEmpty(message = "At least one target asset is required")
        Set<UUID> targetAssetIds,

        @NotNull(message = "Start instant is required")
        Instant startAt,

        @NotNull(message = "End instant is required")
        Instant endAt,

        UUID maintenanceTicketId
) {}
