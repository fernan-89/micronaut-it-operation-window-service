package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.EvaluateWindowCollisionRequest;
import com.thinklab.application.dto.request.InitiateOperationWindowRequest;
import com.thinklab.application.dto.request.RescheduleOperationWindowRequest;
import com.thinklab.application.dto.response.CollisionEvaluationResponse;
import com.thinklab.application.dto.response.OperationWindowResponse;
import com.thinklab.application.usecase.ControlOperationWindowUseCase;
import com.thinklab.application.usecase.EvaluateWindowCollisionUseCase;
import com.thinklab.application.usecase.InitiateOperationWindowUseCase;
import com.thinklab.application.usecase.RescheduleOperationWindowUseCase;
import com.thinklab.application.usecase.RetrieveOperationWindowUseCase;
import com.thinklab.application.usecase.RetrieveOperationWindowsUseCase;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-operation-window} Service Domain — the Control Record is
 * the {@link com.thinklab.domain.model.OperationWindow}.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> every route follows
 * {@code /it-operation-window/v1/{control-record-id}/{behavior-qualifier}}. There is no
 * {@code DELETE}: {@code control/cancel} is a terminal, soft transition. Maintenance tickets are a
 * subordinate resource under {@code /it-operation-window/v1/maintenance-ticket} (see
 * {@link MaintenanceTicketController}).
 *
 * <p><b>Header-Sourced Forensics (ADR-013):</b> {@code X-Tenant-Id} (organisationId) is mandatory on
 * {@code initiate}, the collection {@code retrieve} and {@code collision-check/evaluate};
 * {@code X-Executor} is mandatory on every mutation.
 */
@Controller("/it-operation-window/v1")
public class OperationWindowController {

    private static final Logger log = LoggerFactory.getLogger(OperationWindowController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiateOperationWindowUseCase initiateOperationWindowUseCase;
    private final RetrieveOperationWindowUseCase retrieveOperationWindowUseCase;
    private final RetrieveOperationWindowsUseCase retrieveOperationWindowsUseCase;
    private final RescheduleOperationWindowUseCase rescheduleOperationWindowUseCase;
    private final ControlOperationWindowUseCase controlOperationWindowUseCase;
    private final EvaluateWindowCollisionUseCase evaluateWindowCollisionUseCase;

    public OperationWindowController(
            InitiateOperationWindowUseCase initiateOperationWindowUseCase,
            RetrieveOperationWindowUseCase retrieveOperationWindowUseCase,
            RetrieveOperationWindowsUseCase retrieveOperationWindowsUseCase,
            RescheduleOperationWindowUseCase rescheduleOperationWindowUseCase,
            ControlOperationWindowUseCase controlOperationWindowUseCase,
            EvaluateWindowCollisionUseCase evaluateWindowCollisionUseCase
    ) {
        this.initiateOperationWindowUseCase = initiateOperationWindowUseCase;
        this.retrieveOperationWindowUseCase = retrieveOperationWindowUseCase;
        this.retrieveOperationWindowsUseCase = retrieveOperationWindowsUseCase;
        this.rescheduleOperationWindowUseCase = rescheduleOperationWindowUseCase;
        this.controlOperationWindowUseCase = controlOperationWindowUseCase;
        this.evaluateWindowCollisionUseCase = evaluateWindowCollisionUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Schedules a new Operation Window (409 on collision). */
    @Post("/initiate")
    public Mono<HttpResponse<OperationWindowResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateOperationWindowRequest request
    ) {
        log.info("[ACTION: INITIATE_OPERATION_WINDOW] [EXECUTOR: {}] Received request to schedule window '{}' for organisation: {}",
                executor, request.title(), tenantId);

        return initiateOperationWindowUseCase.execute(UUID.fromString(tenantId), request, executor)
                .map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single Operation Window by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<OperationWindowResponse>> retrieveById(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_OPERATION_WINDOW] Received request to get window by ID: {}", id);

        return retrieveOperationWindowUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists tenant-scoped windows, filterable by status / asset. */
    @Get("/retrieve")
    public Flux<OperationWindowResponse> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable WindowStatus status,
            @QueryValue @Nullable UUID assetId
    ) {
        log.info("[ACTION: RETRIEVE_OPERATION_WINDOWS] Received request to list windows for organisation: {} status: {} asset: {}",
                tenantId, status, assetId);

        return retrieveOperationWindowsUseCase.execute(UUID.fromString(tenantId), status, assetId);
    }

    /** Behavior Qualifier: {@code reschedule}. Moves a SCHEDULED window (409 on collision or wrong status). */
    @Put("/{id}/reschedule")
    public Mono<HttpResponse<Void>> reschedule(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid RescheduleOperationWindowRequest request
    ) {
        log.info("[ACTION: RESCHEDULE_OPERATION_WINDOW] [EXECUTOR: {}] Received request to reschedule window ID: {}", executor, id);

        return rescheduleOperationWindowUseCase.execute(id, request).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/start}. SCHEDULED -> IN_PROGRESS. */
    @Put("/{id}/control/start")
    public Mono<HttpResponse<Void>> controlStart(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlOperationWindowUseCase.Action.START, executor);
    }

    /** Behavior Qualifier: {@code control/complete}. IN_PROGRESS -> COMPLETED (terminal). */
    @Put("/{id}/control/complete")
    public Mono<HttpResponse<Void>> controlComplete(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlOperationWindowUseCase.Action.COMPLETE, executor);
    }

    /** Behavior Qualifier: {@code control/cancel}. SCHEDULED | IN_PROGRESS -> CANCELLED (terminal, no DELETE). */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlOperationWindowUseCase.Action.CANCEL, executor);
    }

    /** Behavior Qualifier: {@code collision-check/evaluate}. Read-only dry-run of a candidate interval. */
    @Post("/collision-check/evaluate")
    public Mono<HttpResponse<CollisionEvaluationResponse>> evaluateCollision(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Body @Valid EvaluateWindowCollisionRequest request
    ) {
        log.info("[ACTION: EVALUATE_WINDOW_COLLISION] Received dry-run for organisation: {}", tenantId);

        return evaluateWindowCollisionUseCase.execute(UUID.fromString(tenantId), request).map(HttpResponse::ok);
    }

    private Mono<HttpResponse<Void>> control(UUID id, ControlOperationWindowUseCase.Action action, String executor) {
        log.info("[ACTION: CONTROL_OPERATION_WINDOW] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return controlOperationWindowUseCase.execute(id, action).thenReturn(HttpResponse.noContent());
    }
}
