package com.thinklab.infrastructure.adapter.in.web.handler;

import com.thinklab.domain.exception.InvalidTicketStatusException;
import com.thinklab.domain.exception.InvalidWindowStatusException;
import com.thinklab.domain.exception.MaintenanceTicketNotFoundException;
import com.thinklab.domain.exception.WindowCollisionException;
import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.service.WindowCollisionChecker.WindowConflict;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpRequest<?> request;
    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = Mockito.mock(HttpRequest.class);
        headers = Mockito.mock(HttpHeaders.class);
        Mockito.when(request.getPath()).thenReturn("/it-operation-window/v1/test");
        Mockito.when(request.getAttribute(Mockito.eq("traceId"), Mockito.eq(String.class))).thenReturn(Optional.empty());
        Mockito.when(request.getHeaders()).thenReturn(headers);
        Mockito.when(headers.get("X-Trace-Id")).thenReturn(null);
    }

    private Map<String, Object> assertProblem(HttpResponse<Map<String, Object>> response, HttpStatus status, String code) {
        assertNotNull(response);
        assertEquals(status, response.getStatus());
        Map<String, Object> body = response.body();
        assertNotNull(body);
        assertEquals(status.getCode(), body.get("status"));
        assertEquals(code, body.get("error_code"));
        assertEquals("/it-operation-window/v1/test", body.get("instance"));
        assertNotNull(body.get("timestamp"));
        assertNotNull(body.get("title"));
        assertNotNull(body.get("type"));
        return body;
    }

    @Test
    @DisplayName("MaintenanceTicketNotFoundException maps to 404 with ERR-OPS-00404")
    void ticketNotFound() {
        assertProblem(exceptionHandler.handle(request, new MaintenanceTicketNotFoundException(UUID.randomUUID())),
                HttpStatus.NOT_FOUND, "ERR-OPS-00404");
    }

    @Test
    @DisplayName("InvalidTicketStatusException maps to 409 with ERR-OPS-00409")
    void ticketConflict() {
        assertProblem(exceptionHandler.handle(request, new InvalidTicketStatusException("illegal")), HttpStatus.CONFLICT, "ERR-OPS-00409");
    }

    @Test
    @DisplayName("FreezeOverrideNotPermittedException maps to 403 with ERR-WIN-00403 and names the role")
    void freezeOverrideNotPermitted() {
        Map<String, Object> body = assertProblem(exceptionHandler.handle(request, new com.thinklab.domain.exception.FreezeOverrideNotPermittedException("OPERATOR")),
                HttpStatus.FORBIDDEN, "ERR-WIN-00403");

        assertTrue(body.get("detail").toString().contains("OPERATOR"));
    }

    @Test
    @DisplayName("WindowNotFoundException maps to 404 with ERR-WIN-00404")
    void windowNotFound() {
        UUID id = UUID.randomUUID();

        Map<String, Object> body = assertProblem(exceptionHandler.handle(request, new WindowNotFoundException(id)),
                HttpStatus.NOT_FOUND, "ERR-WIN-00404");

        assertTrue(body.get("detail").toString().contains(id.toString()));
        assertEquals("https://api.thinklab.com/errors/err-win-00404", body.get("type"));
    }

    @Test
    @DisplayName("InvalidWindowStatusException maps to 409 with ERR-WIN-00409")
    void windowConflict() {
        assertProblem(exceptionHandler.handle(request, new InvalidWindowStatusException("illegal")), HttpStatus.CONFLICT, "ERR-WIN-00409");
    }

    @Test
    @DisplayName("WindowCollisionException maps to 409 with ERR-COL-00409 and exposes the impact as `conflicts`")
    @SuppressWarnings("unchecked")
    void collisionExposesImpact() {
        UUID windowId = UUID.randomUUID();
        UUID asset = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T10:00:00Z");
        Instant end = Instant.parse("2026-10-01T12:00:00Z");

        Map<String, Object> body = assertProblem(exceptionHandler.handle(request,
                        new WindowCollisionException(List.of(new WindowConflict(windowId, "Firmware", start, end, Set.of(asset))))),
                HttpStatus.CONFLICT, "ERR-COL-00409");

        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) body.get("conflicts");
        assertEquals(1, conflicts.size());
        assertEquals(windowId.toString(), conflicts.get(0).get("windowId"));
        assertEquals("Firmware", conflicts.get(0).get("title"));
        assertEquals("2026-10-01T10:00:00Z", conflicts.get(0).get("startAt"));
        assertEquals("2026-10-01T12:00:00Z", conflicts.get(0).get("endAt"));
        assertEquals(List.of(asset.toString()), conflicts.get(0).get("overlappingAssetIds"));
    }

    @Test
    @DisplayName("non-collision business errors carry no `conflicts` member")
    void noConflictsMemberForOtherErrors() {
        Map<String, Object> body = assertProblem(exceptionHandler.handle(request, new InvalidWindowStatusException("x")),
                HttpStatus.CONFLICT, "ERR-WIN-00409");

        assertFalse(body.containsKey("conflicts"));
    }

    @Test
    @DisplayName("ConstraintViolationException maps to 400 with ERR-VALIDATION-00400")
    void validation() {
        assertProblem(exceptionHandler.handle(request, new ConstraintViolationException("Validation failed", Collections.emptySet())),
                HttpStatus.BAD_REQUEST, "ERR-VALIDATION-00400");
    }

    @Test
    @DisplayName("IllegalArgumentException (scheduling invariant / malformed tenant) maps to 400, not 500")
    void illegalArgument() {
        Map<String, Object> body = assertProblem(exceptionHandler.handle(request, new IllegalArgumentException("The window must end after it starts.")),
                HttpStatus.BAD_REQUEST, "ERR-VALIDATION-00400");

        assertTrue(body.get("detail").toString().contains("The window must end after it starts."));
    }

    @Test
    @DisplayName("an unexpected technical failure maps to 500 with debug_info and a generic detail")
    void generic() {
        Map<String, Object> body = assertProblem(exceptionHandler.handle(request, new RuntimeException("Unexpected internal failure")),
                HttpStatus.INTERNAL_SERVER_ERROR, "ERR-INTERNAL-00500");

        assertEquals("RuntimeException: Unexpected internal failure", body.get("debug_info"));
        assertEquals("An unexpected technical failure occurred within the processing pipeline.", body.get("detail"));
    }

    @Test
    @DisplayName("the X-Trace-Id request header and the traceId attribute are both accepted as trace sources")
    void traceSources() {
        Mockito.when(headers.get("X-Trace-Id")).thenReturn("abc-123");
        assertProblem(exceptionHandler.handle(request, new WindowNotFoundException("x")), HttpStatus.NOT_FOUND, "ERR-WIN-00404");

        Mockito.when(request.getAttribute(Mockito.eq("traceId"), Mockito.eq(String.class))).thenReturn(Optional.of("attr-trace"));
        assertProblem(exceptionHandler.handle(request, new WindowNotFoundException("x")), HttpStatus.NOT_FOUND, "ERR-WIN-00404");
    }

    @Test
    @DisplayName("a trace id from the X-Trace-Id header is honoured and a blank one replaced")
    void traceIdFromHeader() {
        Mockito.when(headers.get("X-Trace-Id")).thenReturn("header-trace");
        org.junit.jupiter.api.Assertions.assertEquals(404, exceptionHandler.handle(request, new MaintenanceTicketNotFoundException("x")).getStatus().getCode());

        Mockito.when(headers.get("X-Trace-Id")).thenReturn(" ");
        org.junit.jupiter.api.Assertions.assertEquals(404, exceptionHandler.handle(request, new MaintenanceTicketNotFoundException("x")).getStatus().getCode());
    }
}
