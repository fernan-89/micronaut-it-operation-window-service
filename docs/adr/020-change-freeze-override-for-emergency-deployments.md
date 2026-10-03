# ADR-020: Audited CHANGE_FREEZE Override for DEPLOYMENT Windows

## Status
Accepted

## Context
ADR-018's collision rule treats a `CHANGE_FREEZE` window like any other: no window may overlap it on a
shared asset. That is right for planned work, and wrong for an emergency: an ECAB-approved EMERGENCY
change (see change-management-service) that must go in during a freeze had no automated path - someone
had to edit or cancel the freeze out of band, which is slower and leaves no record tying the exception to
the change that needed it.

## Decision
- `POST /initiate` accepts an optional `changeFreezeOverrideJustification` (max 500 characters).
  When present, `CHANGE_FREEZE` windows are not compared during the collision check. **Every other active
  window still collides** - the override waives the freeze, not the schedule.
- Only a `DEPLOYMENT` window may carry an override, and the justification must not be blank; anything
  else is a 400 before any I/O. Reschedule and the collision-check dry run are unchanged (no override).
- The override is traceable on the record itself: the window's `description` gets a line
  `[CHANGE_FREEZE OVERRIDE] <justification>`, and the service logs it at WARN with tenant, executor and
  assets.
- **Authorization lives with the caller.** This service cannot verify that an ECAB approved anything; it
  trusts the platform's authenticated callers (same trust boundary as every other write). The caller that
  uses this - change-management-service - only sends it for an EMERGENCY ChangeRequest that has already
  reached `APPROVED` through the ECAB quorum (its ADR-034).

## Consequences
- Positive: an approved emergency change can be scheduled during a freeze without touching the freeze.
- Positive: the exception is greppable (`CHANGE_FREEZE OVERRIDE`) on both the window and the change.
- Negative: any caller allowed to `initiate` can claim an override; with security enabled this is only as
  strict as the role check on that endpoint. A dedicated role for the override is the next step if that
  proves too loose.
- Negative: the justification is free text stored in `description`, not a structured field; fine for
  audit reading, not for reporting.
