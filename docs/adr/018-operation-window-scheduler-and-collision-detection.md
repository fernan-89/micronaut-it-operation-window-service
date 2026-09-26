# ADR-018: Operation Window Scheduler, Collision Detection and Scope of the Service Domain

## Status
Accepted

## Context
The roadmap and the Master Tasks Catalog define this Service Domain as the **Operation Window
scheduler** (OPS-01) with **reactive collision and impact checking** (OPS-02). The first
implementation committed here (`8ebbf8f`) instead delivered a *Maintenance Ticket* workflow
(`OPEN -> IN_ANALYSIS <-> AWAITING_PARTS -> COMPLETED`). That code is correct and tested, but it is a
different capability from the one the roadmap names, and it shipped ahead of its stated prerequisite
(the IT Asset Registry).

## Decision

### Scope
The service is **the Operation Window Service Domain**. Its Control Record is the `OperationWindow`.
The maintenance ticket is kept — it has real value and passing tests — but is re-homed as a
**subordinate resource** of the domain, exactly as `OrganisationUnit` was promoted to an addressable
subordinate resource of the Party Reference Data Directory (ADR-015):

| Resource | Base path |
|---|---|
| `OperationWindow` (Control Record) | `/it-operation-window/v1` |
| `MaintenanceTicket` (subordinate) | `/it-operation-window/v1/maintenance-ticket` |

An `OperationWindow` may reference a ticket through the optional `maintenanceTicketId`; no
synchronous cross-aggregate validation is performed (the id is opaque, same rule as `assetId`).
The ticket routes moved under the new prefix; no client depended on the old root paths yet.

### Scheduling invariants (OPS-01) — enforced by the aggregate
* an interval is `[startAt, endAt)`, `endAt` strictly after `startAt`;
* duration between **5 minutes** and **72 hours**;
* a window cannot start in the past (one-minute tolerance);
* 1–50 target assets;
* `reschedule` is only legal while `SCHEDULED`.

Lifecycle: `SCHEDULED -> IN_PROGRESS -> COMPLETED`, and `SCHEDULED | IN_PROGRESS -> CANCELLED`;
`COMPLETED` and `CANCELLED` are terminal. There is no `DELETE` — `control/cancel` is a soft, terminal
transition (ADR-013).

### Collision detection and impact (OPS-02)
Two windows collide when **all** hold: both are *active* (`SCHEDULED` or `IN_PROGRESS`), they share
at least one target asset, and their intervals overlap. Overlap is **half-open**, so back-to-back
windows (`A.end == B.start`) do not collide — a planner can chain operations on the same asset.

The rule lives in a pure domain service, `WindowCollisionChecker`, not in a database query. The
persistence port only returns a coarse candidate set (tenant + active + time overlap); the domain
applies the exact rule and computes the **impact**: which windows are hit and on which assets. A
collision is reported as `WindowCollisionException` → HTTP **409**, `error_code` **`ERR-COL-00409`**,
and the RFC 7807 document carries a `conflicts` extension member with the full impact list.

A read-only `POST /collision-check/evaluate` runs the same check as a dry-run (optionally excluding
a window, to ask "would moving X here collide?") so a planner can iterate on a slot before committing.

### Error catalog
| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-WIN-00404` | 404 | Window not found |
| `ERR-WIN-00409` | 409 | Illegal / idempotent window transition, or reschedule of a non-SCHEDULED window |
| `ERR-COL-00409` | 409 | Requested interval collides with active windows (`conflicts` member) |
| `ERR-OPS-00404` / `ERR-OPS-00409` | 404 / 409 | Maintenance ticket not found / illegal ticket transition |
| `ERR-VALIDATION-00400` | 400 | Bean validation or a scheduling invariant (also a malformed `X-Tenant-Id`) |

## Known limitation
Check-then-write is **not atomic**: two planners submitting colliding windows in the same instant can
both pass the check. Closing it needs a per-asset lease/lock (for example a unique-interval guard
collection) and is deliberately out of scope for v1; the `collision-check/evaluate` dry-run and the
ordered `conflicts` list make any residual overlap detectable and correctable. Asset filtering on the
collection `retrieve` is done in memory after the tenant/status query (windows per tenant are
few); an indexed array query can replace it behind the same port if that ever changes.

## Consequences
- Positive: the service now matches the roadmap; the collision rule is unit-tested as pure logic
  (including half-open edges, containment, inactive windows, self-exclusion and ordering); planners
  get actionable impact instead of a bare refusal.
- Negative: two aggregates live in one service, and the ticket routes changed. Splitting the
  maintenance ticket into its own service later is possible without touching the window model.

## Addendum: indexes
The collision query (`organisationId` equality, `status` `$in` the active statuses, `startAt < end`,
`endAt > start`) is served by the compound index `{organisationId: 1, status: 1, startAt: 1}` on
`operation_windows`, declared with `@Indexes` on the entity and created at startup by
thinklab-service-kit's `MongoIndexInitializer` (kit ADR-005). Field order follows equality, then `$in`,
then range; `endAt` is filtered from the scanned entries. The tenant listings use the same index by
prefix, and `maintenance_tickets` gets `{organisationId: 1, status: 1}` for its own. The integration
suite asserts that both indexes exist and that the collision query's winning plan is an `IXSCAN` on the
compound index.
