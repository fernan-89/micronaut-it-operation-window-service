package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CaptureCommentRequest;
import com.thinklab.application.dto.request.InitiateMaintenanceTicketRequest;
import com.thinklab.application.dto.response.MaintenanceTicketResponse;
import com.thinklab.application.usecase.CaptureMaintenanceCommentUseCase;
import com.thinklab.application.usecase.ControlMaintenanceTicketUseCase;
import com.thinklab.application.usecase.InitiateMaintenanceTicketUseCase;
import com.thinklab.application.usecase.RetrieveMaintenanceTicketUseCase;
import com.thinklab.application.usecase.RetrieveMaintenanceTicketsUseCase;
import com.thinklab.domain.model.MaintenanceTicket.TicketStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-operation-window} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model:</b> {@link com.thinklab.domain.model.MaintenanceTicket} is
 * the Control Record. Every route follows
 * {@code /it-operation-window/v1/maintenance-ticket/{control-record-id}/{behavior-qualifier}} (a subordinate resource of the Operation Window Service Domain). There is no
 * {@code DELETE}: {@code control/{status}} and {@code comment/initiate} are the only mutation
 * paths, and {@code COMPLETED} is a terminal, non-exitable state.
 */
@Controller("/it-operation-window/v1/maintenance-ticket")
public class MaintenanceTicketController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTicketController.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiateMaintenanceTicketUseCase initiateMaintenanceTicketUseCase;
    private final RetrieveMaintenanceTicketUseCase retrieveMaintenanceTicketUseCase;
    private final RetrieveMaintenanceTicketsUseCase retrieveMaintenanceTicketsUseCase;
    private final CaptureMaintenanceCommentUseCase captureMaintenanceCommentUseCase;
    private final ControlMaintenanceTicketUseCase controlMaintenanceTicketUseCase;

    public MaintenanceTicketController(
            InitiateMaintenanceTicketUseCase initiateMaintenanceTicketUseCase,
            RetrieveMaintenanceTicketUseCase retrieveMaintenanceTicketUseCase,
            RetrieveMaintenanceTicketsUseCase retrieveMaintenanceTicketsUseCase,
            CaptureMaintenanceCommentUseCase captureMaintenanceCommentUseCase,
            ControlMaintenanceTicketUseCase controlMaintenanceTicketUseCase
    ) {
        this.initiateMaintenanceTicketUseCase = initiateMaintenanceTicketUseCase;
        this.retrieveMaintenanceTicketUseCase = retrieveMaintenanceTicketUseCase;
        this.retrieveMaintenanceTicketsUseCase = retrieveMaintenanceTicketsUseCase;
        this.captureMaintenanceCommentUseCase = captureMaintenanceCommentUseCase;
        this.controlMaintenanceTicketUseCase = controlMaintenanceTicketUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Opens a new MaintenanceTicket Control Record. */
    @Post("/initiate")
    public Mono<HttpResponse<MaintenanceTicketResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateMaintenanceTicketRequest request
    ) {
        log.info("[ACTION: INITIATE_MAINTENANCE_TICKET] [EXECUTOR: {}] Received request to open ticket for asset: {}", executor, request.assetId());

        return initiateMaintenanceTicketUseCase.execute(UUID.fromString(tenantId), request)
                .map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single MaintenanceTicket by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<MaintenanceTicketResponse>> retrieveById(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_MAINTENANCE_TICKET] Received request to get ticket by ID: {}", id);

        return retrieveMaintenanceTicketUseCase.execute(id)
                .map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists tenant-scoped MaintenanceTickets. */
    @Get("/retrieve")
    public Flux<MaintenanceTicketResponse> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable TicketStatus status
    ) {
        log.info("[ACTION: RETRIEVE_MAINTENANCE_TICKETS] Received request to list tickets for organisation: {} [status={}]", tenantId, status);

        return retrieveMaintenanceTicketsUseCase.execute(UUID.fromString(tenantId), status);
    }

    /** Behavior Qualifier: {@code comment/initiate}. Captures a comment, optionally advancing the FSM. */
    @Post("/{id}/comment/initiate")
    @Status(HttpStatus.CREATED)
    public Mono<Void> initiateComment(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid CaptureCommentRequest request
    ) {
        log.info("[ACTION: CAPTURE_MAINTENANCE_COMMENT] [EXECUTOR: {}] Received comment for ticket: {}", executor, id);

        return captureMaintenanceCommentUseCase.execute(id, executor, request);
    }

    /** Behavior Qualifier: {@code control/{targetStatus}}. Direct lifecycle control, independent of comments. */
    @Put("/{id}/control/{targetStatus}")
    public Mono<HttpResponse<Void>> control(
            @PathVariable UUID id,
            @PathVariable TicketStatus targetStatus,
            @Header(EXECUTOR_HEADER) @NotBlank String executor
    ) {
        log.info("[ACTION: CONTROL_MAINTENANCE_TICKET] [EXECUTOR: {}] {} for ticket: {}", executor, targetStatus, id);

        return controlMaintenanceTicketUseCase.execute(id, targetStatus)
                .thenReturn(HttpResponse.noContent());
    }
}
