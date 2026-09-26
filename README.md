# Thinklab IT Operation Window Service

**Version:** 1.0.0

**Status:** Reference implementation — portfolio project

## Overview

The Thinklab IT Operation Window Service plans and polices **when** IT assets may be operated on. It
implements the BIAN-aligned `it-operation-window` Service Domain (ADR-013): the `OperationWindow` is
the Control Record — a bounded interval during which one or more assets (opaque ids from the IT Asset
Registry) are under a planned operation such as maintenance, patching, migration or a change freeze.
Its defining capability is **reactive collision and impact detection**: a window can never be
scheduled on top of another active window that targets the same asset, and the planner is told exactly
what it would collide with (ADR-018).

The **maintenance ticket** workflow lives in the same domain as a subordinate resource
(`/it-operation-window/v1/maintenance-ticket`).

Every window is scoped to an Organisation from the Party Reference Data Directory (`X-Tenant-Id`) and
receives its sovereign UUID from the Hash Token Registry. Built with Java 21 and Micronaut 4.4.2 on a
strict Hexagonal Architecture and a fully reactive stack (Project Reactor, Micronaut Data MongoDB).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Micronaut Data MongoDB (`operation_windows`, `maintenance_tickets`) with optimistic locking (`@Version`)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (exhaustive FSM matrices, collision-rule scenarios, use cases, controllers, adapters, handler)

## Domain Model

```text
OperationWindow { id, organisationId, title, description?, windowType, targetAssetIds[1..50],
                  startAt, endAt, status, maintenanceTicketId?, requestedBy, createdAt, updatedAt }
windowType: MAINTENANCE | PATCHING | DEPLOYMENT | MIGRATION | CHANGE_FREEZE
status:     SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED
```

```text
SCHEDULED -> IN_PROGRESS -> COMPLETED (terminal)
SCHEDULED | IN_PROGRESS -> CANCELLED (terminal, soft — no DELETE)
```

* Interval `[startAt, endAt)`: 5 minutes to 72 hours, not in the past. `reschedule` only while `SCHEDULED`.
* **Collision:** same asset + both windows active (`SCHEDULED`/`IN_PROGRESS`) + overlapping interval.
  Back-to-back windows (`A.end == B.start`) do **not** collide.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on `initiate`, the collection `retrieve` and `collision-check/evaluate`;
`X-Executor` on every mutation.

### Operation Window — `/it-operation-window/v1`

| Behavior Qualifier | Method & Path |
|---|---|
| initiate (409 on collision) | `POST /it-operation-window/v1/initiate` |
| retrieve (single) | `GET /it-operation-window/v1/{id}/retrieve` |
| retrieve (collection, filters `status`, `assetId`) | `GET /it-operation-window/v1/retrieve` |
| reschedule | `PUT /it-operation-window/v1/{id}/reschedule` |
| control/start, complete, cancel | `PUT /it-operation-window/v1/{id}/control/{action}` |
| collision-check/evaluate (dry-run) | `POST /it-operation-window/v1/collision-check/evaluate` |

### Maintenance Ticket (subordinate) — `/it-operation-window/v1/maintenance-ticket`

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST .../maintenance-ticket/initiate` |
| retrieve (single / collection) | `GET .../maintenance-ticket/{id}/retrieve`, `GET .../maintenance-ticket/retrieve` |
| comment/initiate (may advance the FSM) | `POST .../maintenance-ticket/{id}/comment/initiate` |
| control/{status} | `PUT .../maintenance-ticket/{id}/control/{OPEN\|IN_ANALYSIS\|AWAITING_PARTS\|COMPLETED}` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-WIN-00404` | 404 | Window not found |
| `ERR-WIN-00409` | 409 | Illegal/idempotent transition, or reschedule of a non-SCHEDULED window |
| `ERR-COL-00409` | 409 | Collision — the problem document carries a `conflicts` array (the impact) |
| `ERR-OPS-00404` / `ERR-OPS-00409` | 404 / 409 | Maintenance ticket not found / illegal transition |
| `ERR-VALIDATION-00400` | 400 | Validation or scheduling-invariant failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example — schedule a window, then ask whether an overlapping slot is free:

```bash
curl -X POST http://localhost:8084/it-operation-window/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -H "X-Executor: planner-01" \
  -d '{"title":"Core switch firmware","windowType":"PATCHING","targetAssetIds":["0b7b2c1e-7a55-4d59-8d6e-1f0d3b2f4a11"],"startAt":"2026-12-01T02:00:00Z","endAt":"2026-12-01T04:00:00Z"}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8084)
./gradlew run

# Container image
docker build -t thinklab-operation-window-service:latest .
```

* **Health:** `http://localhost:8084/health`
* **Swagger UI:** `http://localhost:8084/swagger-ui`
* **Postman suite:** `docs/postman/` (scheduling, collision, tickets, negative scenarios — every date is computed by pre-request scripts)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8084` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_operation_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 018 operation window scheduler, collision detection and scope.

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.
