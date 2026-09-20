package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * DTO for moving a SCHEDULED Operation Window (BIAN Behavior Qualifier: {@code reschedule}).
 */
@Serdeable
public record RescheduleOperationWindowRequest(

        @NotNull(message = "Start instant is required")
        Instant startAt,

        @NotNull(message = "End instant is required")
        Instant endAt
) {}
