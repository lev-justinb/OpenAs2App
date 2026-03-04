# Data Model: On-Demand Poll Trigger

**Feature**: 001-trigger-poll-now  
**Date**: 2026-03-04

This feature does not introduce new persistent entities or storage. The following describe in-memory and API payload concepts.

## Concepts

### Poll trigger request

- **Source**: REST caller (operator or system) via `POST /api/poll/trigger` (optional query or body parameters for “includeResult” or similar may be added).
- **Identity**: No durable id; one request per HTTP call.
- **Lifecycle**: Request is handled synchronously; response returned in the same HTTP response.

### Poll run result (per poller or aggregate)

- **Attributes**:
  - **success** (boolean): Whether the poll cycle completed without error.
  - **message** (string, optional): Short human-readable summary (e.g. “Poll completed” or “Poll failed: &lt;reason&gt;”).
  - **pollerCount** (integer, optional): Number of pollers triggered (for aggregate response).
- **Used for**: Optional notification to the activator via REST response body (e.g. `CommandResult` with type OK/ERROR and results list).
- **Lifecycle**: Created per trigger request; not persisted.

### Polling module (existing)

- **Relevant state**: Timer (next scheduled run), busy flag (poll in progress).
- **Behavior change**: New method `triggerPollNow()`: run poll immediately, then cancel and reschedule timer so next run is one interval from now. Invariant: no duplicate processing of the same file in the same run; concurrent trigger and timer use same busy guard.

## Validation rules (from spec)

- Trigger is only accepted when the server is running and the REST API is enabled and the caller is authenticated (existing auth).
- No new persistence or schema; no new entities in config.xml beyond optional command registration in `commands.xml`.
