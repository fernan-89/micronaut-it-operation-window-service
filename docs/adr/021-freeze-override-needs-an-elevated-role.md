# ADR-021: Granting a CHANGE_FREEZE Override Needs an Elevated Role

## Status
Accepted (tightens ADR-020)

## Context
ADR-020 lets a `DEPLOYMENT` window be reserved over a `CHANGE_FREEZE` when `initiate` carries a
`changeFreezeOverrideJustification`, and noted that this service trusts its callers: any caller allowed to `initiate` could claim an
override.

## Decision
- With security on, an `initiate` that carries a `changeFreezeOverrideJustification` requires the **verified role** (`X-Role`, set by
  the kit's security filter from the token) to be `ADMIN` or `SERVICE` (`FreezeOverridePolicy`). Anything else is
  `403 ERR-WIN-00403`, checked first, before any I/O. change-management-service calls with a service token (`SERVICE`), so the
  legitimate caller is unaffected, and its own check (its ADR-035) already restricts who can ask.
- With security off there is no role (`null`) and nothing is enforced.
- A window without an override needs no elevated role.

## Consequences
- Positive: a direct caller with an ordinary OPERATOR token can no longer waive a freeze by adding a field.
- Negative: ADMIN is coarse (see change-management ADR-035); a dedicated role needs a kit release and a rollout and is the follow-up.
