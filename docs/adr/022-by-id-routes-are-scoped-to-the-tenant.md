# ADR-022: The By-Id Routes Are Scoped to the Tenant

## Status
Accepted (2026-11-17). Part of the tenant audit of the platform (thinklab-platform `docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037, change management ADR-038, asset registry ADR-029 and hardware maintenance ADR-036.

## Context
Every route of a window or of a maintenance ticket that takes the id found it by id alone. The cross-tenant probe (platform `scripts/e2e/tenant-probe.sh`) showed a window of tenant X answering 200 to tenant Y on retrieve.

## Decision
- Every by-id route of a window and of a maintenance ticket takes `X-Tenant-Id` (required, 400 if blank) and finds the object only in that tenant: another tenant's is the same as none (404), nothing is read or written. The repositories have no unscoped `findById` any more (a derived `findByIdAndOrganisationId` query of Micronaut Data does the lookup).
- The reservation and collision routes already took the tenant and are unchanged. Change management reserves a window with the tenant of the change.

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's window or ticket.
- Negative: a client that sends no tenant on a by-id route now gets a 400.
