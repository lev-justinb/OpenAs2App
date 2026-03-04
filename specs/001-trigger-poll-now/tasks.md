---
description: "Task list for On-Demand Poll Trigger feature"
---

# Tasks: On-Demand Poll Trigger

**Input**: Design documents from `specs/001-trigger-poll-now/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included per constitution (automated tests at an appropriate level for the change).

**Organization**: Tasks are grouped by user story so each story can be implemented and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story (US1, US2) for story-phase tasks only
- Include exact file paths in descriptions

## Path Conventions

- **Server module**: `Server/src/main/java/org/openas2/`, `Server/src/test/java/org/openas2/`
- **Config**: `Server/src/config/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare package and structure for the new poll command.

- [ ] T001 Create package directory for poll commands at Server/src/main/java/org/openas2/app/poll/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core changes that MUST be complete before the trigger command can work.

**⚠️ CRITICAL**: User story implementation depends on this phase.

- [ ] T002 Add `triggerPollNow()` to PollingModule in Server/src/main/java/org/openas2/processor/receiver/PollingModule.java: run poll immediately, cancel current timer, reschedule next run one full interval from now; use existing busy guard (if poll already running, only reschedule timer; otherwise run poll then reschedule).

- [ ] T003 Add a method to return all outbound polling modules: in BaseSession (and Session interface if needed) add a method that returns a list of PollingModule instances from (1) partnership pollers in getPolledDirectories() (each entry’s pollerInstance), (2) processor.getModules() filtered by PollingModule.class.isAssignableFrom. Implement in Server/src/main/java/org/openas2/BaseSession.java (and org/openas2/Session.java if Session is an interface).

**Checkpoint**: Foundation ready — TriggerPollCommand can call triggerPollNow() and obtain all pollers.

---

## Phase 3: User Story 1 - Trigger Poll Immediately and Reset Timer (Priority: P1) 🎯 MVP

**Goal**: Operator or system can call the REST API to trigger an immediate poll for all outbound pollers; each poller’s timer is reset so the next scheduled poll is one interval later.

**Independent Test**: POST (or GET) to /api/poll/trigger with valid admin auth returns 200; poll runs within seconds; next scheduled poll occurs one configured interval after that run.

### Implementation for User Story 1

- [ ] T004 [US1] Implement TriggerPollCommand in Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java: implement Command (getName() "trigger"), obtain Session from processor, get all outbound pollers via session helper, call triggerPollNow() on each, return CommandResult TYPE_OK with results containing a message like "Poll completed for N poller(s).".

- [ ] T005 [US1] Register the poll command in Server/src/config/commands.xml: add a multicommand with name "poll" and description for poll trigger, containing the command class org.openas2.app.poll.TriggerPollCommand.

- [ ] T006 [US1] Add logging for on-demand trigger: in TriggerPollCommand and/or PollingModule.triggerPollNow() log trigger invocation and outcome (e.g. number of pollers triggered, errors) per constitution observability, without logging full payloads.

**Checkpoint**: User Story 1 is done when POST /api/poll/trigger runs all pollers and resets their timers; no server restart required.

---

## Phase 4: User Story 2 - Optional Success/Failure Notification (Priority: P2)

**Goal**: The activator receives a clear success or failure indication in the REST response body (synchronous CommandResult).

**Independent Test**: When notification is used (response body), type is "OK" or "ERROR" and results contain a brief message; on failure, type is "ERROR" with an explanatory message.

### Implementation for User Story 2

- [ ] T007 [US2] In TriggerPollCommand, handle exceptions and failure cases: catch exceptions when gathering pollers or calling triggerPollNow(), return CommandResult TYPE_ERROR with a brief message in results (e.g. "Poll failed: &lt;reason&gt;") per specs/001-trigger-poll-now/contracts/rest-poll-trigger.md.

- [ ] T008 [US2] Ensure response shape matches contract: CommandResult with type "OK" or "ERROR" and results list; when no pollers are configured, return OK with message indicating 0 pollers (or document as acceptable). Verify in Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java.

**Checkpoint**: User Story 2 is done when success and failure responses are clearly distinguishable from the response body.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Tests and documentation required by constitution and quickstart.

- [ ] T009 [P] Add unit test for TriggerPollCommand in Server/src/test/java/org/openas2/app/poll/TriggerPollCommandTest.java: test execute() with mocked session/processor and pollers, verify CommandResult type and message, and error path when session or poll throws.

- [ ] T010 [P] Add integration or REST test for POST /api/poll/trigger in Server/src/test/java (e.g. alongside existing RestApiTest pattern): authenticate, call endpoint, assert 200 and response body has type and results; optionally assert 401 without auth.

- [ ] T011 Run quickstart validation from specs/001-trigger-poll-now/quickstart.md: trigger endpoint with curl (or equivalent), confirm response and that poll runs.

- [ ] T012 [P] Update documentation: if the project documents REST admin endpoints (README, docs folder), add the trigger endpoint (POST /api/poll/trigger, auth, response shape) per specs/001-trigger-poll-now/contracts/rest-poll-trigger.md.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS User Story 1 and 2.
- **Phase 3 (US1)**: Depends on Phase 2 — trigger command and registration.
- **Phase 4 (US2)**: Depends on Phase 3 — error handling and response shape build on TriggerPollCommand.
- **Phase 5 (Polish)**: Depends on Phase 4 — tests and docs after behavior is implemented.

### User Story Dependencies

- **User Story 1 (P1)**: After Foundational; no dependency on US2.
- **User Story 2 (P2)**: After US1; extends same command with failure handling and response contract.

### Within Each User Story

- T004 before T005 (command exists before registration).
- T006 can be done with T004/T005 (logging in same or related files).
- T007, T008 in US2 both touch TriggerPollCommand.

### Parallel Opportunities

- T009 and T010 (tests in different files) can run in parallel.
- T012 (docs) can run in parallel with T009/T010/T011.

---

## Parallel Example: User Story 1

```bash
# After Foundational is done, implementation order:
# T004 (TriggerPollCommand) then T005 (commands.xml) then T006 (logging)
# No parallel within US1 (same command file).
```

## Parallel Example: Polish

```bash
# Run in parallel:
# T009 TriggerPollCommandTest.java
# T010 REST test for /api/poll/trigger
# T012 Documentation update
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup  
2. Complete Phase 2: Foundational  
3. Complete Phase 3: User Story 1  
4. **STOP and VALIDATE**: Call POST /api/poll/trigger, confirm poll runs and timer resets  
5. Optionally run T011 quickstart validation  

### Incremental Delivery

1. Setup + Foundational → triggerPollNow() and poller list available  
2. User Story 1 → Trigger command and registration → MVP (trigger works, basic response)  
3. User Story 2 → Error handling and response contract → Caller can distinguish success/failure  
4. Polish → Tests and docs → Release-ready  

### Single-Developer Order

T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009, T010, T012 (parallel) → T011  

---

## Notes

- [P] tasks: T009, T010, T012 (different files).
- [US1] / [US2] labels map to spec user stories.
- Each user story is independently testable per checkpoint.
- Commit after each task or logical group.
- Constitution: no new dependencies; tests and logging required.
