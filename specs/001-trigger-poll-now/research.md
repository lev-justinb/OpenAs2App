# Research: On-Demand Poll Trigger

**Feature**: 001-trigger-poll-now  
**Date**: 2026-03-04

## Decisions

### 1. How to expose the trigger

- **Decision**: Use the existing REST command pipeline: add a command named `poll` with action `trigger`, so that `POST /api/poll/trigger` (and GET with action) is handled by `ApiResource.processRequest` → `feedCommand("poll", ["trigger"])`. No new JAX-RS resource class.
- **Rationale**: Matches existing pattern (cert, partner, partnership, log, messages); keeps one place for auth and routing; constitution forbids unnecessary complexity.
- **Alternatives considered**: Dedicated REST resource class (rejected—redundant); CLI-only (rejected—spec requires REST).

### 2. How to gather “all configured outbound pollers”

- **Decision**: Collect from (1) session’s partnership pollers (`getPolledDirectories()` → each `pollerInstance`), and (2) processor modules that are `PollingModule` instances (`getProcessor().getModules()` filtered by `PollingModule.class.isAssignableFrom`). Trigger all of them.
- **Rationale**: Partnership pollers live in `BaseSession.polledDirectories`; config can also mount pollers on the processor (see `XMLSession` and `isPollerModule`). Both are “outbound” directory pollers; covering both avoids missing pollers.
- **Alternatives considered**: Only partnership pollers (rejected—processor-mounted pollers would be skipped); only processor modules (rejected—partnership pollers are not on the processor list).

### 3. Timer reset semantics

- **Decision**: Add `triggerPollNow()` on `PollingModule`: run `poll()` immediately (same as timer tick), then cancel the current `Timer` and schedule the next run one full interval from *now* (fixed-rate semantics from trigger time).
- **Rationale**: Spec requires “poll happens right away” and “polling timer is reset” so the next scheduled poll is one interval after the on-demand run. Implementing inside `PollingModule` keeps timer encapsulation and avoids duplicate logic.
- **Alternatives considered**: Reschedule from “completion” of poll (spec says “or after its completion”—chosen “from now” for simplicity and to avoid long runs delaying the next tick); external scheduler (rejected—no new dependencies).

### 4. Concurrency when trigger and timer fire together

- **Decision**: Use existing “busy” guard in `PollingModule`: if a poll is already running (timer or trigger), the trigger run either waits for it to finish then resets the timer, or skips running poll again and only resets the timer (so next run is one interval from now). Prefer “run if not busy, then reset; if busy, only reset timer” to avoid overlapping poll runs and duplicate processing.
- **Rationale**: FR-005 requires safety with concurrent timer-based polls and no duplicate file processing. Existing `PollTask` already gates with `isBusy()`; trigger will use the same contract.
- **Alternatives considered**: Queue trigger (rejected—adds complexity); always run poll even when busy (rejected—risk of duplicate work).

### 5. Optional success/failure notification

- **Decision**: Implement as synchronous response: the REST response body carries a result (e.g. `CommandResult`) indicating overall success or failure, with optional short message (e.g. “Poll completed” vs “Poll failed: …”). No separate callback or push channel.
- **Rationale**: Spec says “optionally notified”; synchronous response is the simplest and fits the existing command/response pattern. Caller can use response status and body; no new infrastructure.
- **Alternatives considered**: Async callback (rejected for scope); separate “status” endpoint (rejected—single trigger call is enough).

### 6. Multiple rapid triggers

- **Decision**: Each trigger request runs the “trigger all pollers” logic once. If a previous trigger (or timer) is still running, behavior follows the “run if not busy, else only reset timer” rule per poller; no request coalescing or queue.
- **Rationale**: Keeps behavior predictable and avoids extra state; rate limiting can be added later if needed.
- **Alternatives considered**: Coalesce (rejected—more state); queue (rejected—unnecessary for typical use).

## Open points for implementation

- Exact HTTP status codes and JSON shape for success vs failure (align with existing `CommandResult` and `ApiResource` JSON).
- Whether `TriggerPollCommand` gets `Session` from the processor’s session reference and how to obtain `BaseSession`-style access to `getPolledDirectories()` (processor has `getSession()`; session impl may need a small helper to list all poller instances if not already present).
