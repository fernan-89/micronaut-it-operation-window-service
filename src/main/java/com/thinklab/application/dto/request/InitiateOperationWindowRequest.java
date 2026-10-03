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
 *
 * <p>{@code changeFreezeOverrideJustification} (ADR-020 of this service): when present, a {@code DEPLOYMENT} window
 * may be reserved over an active {@code CHANGE_FREEZE} window (collisions with every other window still apply).
 * The caller - change-management-service, for an ECAB-approved EMERGENCY change - owns the authorization.
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

        UUID maintenanceTicketId,

        @Size(max = 500, message = "Change-freeze override justification must not exceed 500 characters")
        String changeFreezeOverrideJustification
) {

    /** The pre-override shape: a window that does not ask to override a change freeze. */
    public InitiateOperationWindowRequest(String title, String description, WindowType windowType, Set<UUID> targetAssetIds,
                                          Instant startAt, Instant endAt, UUID maintenanceTicketId) {
        this(title, description, windowType, targetAssetIds, startAt, endAt, maintenanceTicketId, null);
    }
}
