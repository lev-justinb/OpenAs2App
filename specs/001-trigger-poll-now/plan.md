# Implementation Plan: On-Demand Poll Trigger

**Branch**: `001-trigger-poll-now` | **Date**: 2026-03-04 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/001-trigger-poll-now/spec.md`

## Summary

Expose an authenticated REST endpoint on the existing OpenAS2 admin API so operators (or external systems) can trigger an immediate outbox poll for all configured polling modules. When invoked, the server runs the poll cycle right away and resets each poller’s timer so the next scheduled poll is one full interval later. The response may optionally indicate success or failure of the run. Implementation reuses the existing REST command processor and polling modules (no new dependencies); a new “poll” command with “trigger” action invokes a session-level helper that runs and resets all outbound pollers.

## Technical Context

**Language/Version**: Java 11+ (per README and constitution)  
**Primary Dependencies**: Existing stack only—Jersey (REST), Grizzly, Slf4j, OpenAS2 processor/session. No new libraries.  
**Storage**: N/A for this feature (poll state is in-memory; outbox/error dirs unchanged).  
**Testing**: JUnit; existing patterns in `Server/src/test/java` (e.g. `RestApiTest`, `OpenAS2ServerTest`).  
**Target Platform**: JVM (Linux/Windows/server), same as current OpenAS2 server.  
**Project Type**: Web service (AS2 server with embedded REST admin API).  
**Performance Goals**: Trigger response within seconds; poll run duration depends on outbox size and existing poll logic.  
**Constraints**: No new dependencies (constitution); reuse existing REST auth; thread-safe with timer-based polls (no duplicate file processing).  
**Scale/Scope**: Single server; all configured outbound directory pollers (partnership pollers + any processor-mounted pollers).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. AS2 Protocol Correctness & Interoperability**: No change to AS2 message handling, headers, or MDNs; only when poll runs is changed. Pass.
- **II. Message Integrity, Reliability & Traceability**: No change to message flow or persistence; existing poll logic and logging retained. Pass.
- **III. Security by Default**: Trigger exposed only via existing authenticated REST API (same auth as other admin commands). Pass.
- **IV. Operational Observability & Diagnosability**: Trigger invocation and outcome will be logged; optional success/failure in response supports operators. Pass.
- **V. Dependency Minimalism**: No new dependencies; uses existing REST processor, Session, and PollingModule APIs. Pass.
- **System Constraints**: Java 11+, Maven build, file-based config unchanged. Pass.
- **Development Workflow**: Tests and documentation updates will be included per constitution. Pass.

## Project Structure

### Documentation (this feature)

```text
specs/001-trigger-poll-now/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1 (REST API contract)
└── tasks.md             # From /speckit.tasks
```

### Source Code (repository root)

```text
Server/
├── src/main/java/org/openas2/
│   ├── app/poll/                    # New: poll commands
│   │   └── TriggerPollCommand.java
│   ├── BaseSession.java             # Optional: getPollingModules() helper if needed
│   ├── cmd/processor/restapi/
│   │   └── ApiResource.java         # No change; uses resource "poll", action "trigger"
│   └── processor/receiver/
│       └── PollingModule.java        # Add triggerPollNow() and timer reset
├── src/main/resources/ or config/
│   └── commands.xml                 # Register poll/trigger command
└── src/test/java/org/openas2/
    └── app/                         # New: tests for TriggerPollCommand and/or REST
```

**Structure Decision**: Single-module Server project. New code under `org.openas2.app.poll` for the command; changes to `PollingModule` (and possibly `BaseSession`/session interface) for run-now and timer reset. REST is already under `org.openas2.cmd.processor.restapi`; no new REST resource class, only a new command invoked as `POST /api/poll/trigger`.

## Complexity Tracking

No constitution violations. Table left empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
