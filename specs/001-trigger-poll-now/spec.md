# Feature Specification: On-Demand Poll Trigger

**Feature Branch**: `001-trigger-poll-now`  
**Created**: 2026-03-04  
**Status**: Draft  
**Input**: User description: "I want an exposed way (from outside the application) to tell the server to poll for files to send at this particular moment. Instead of waiting for a certain amount of time to elapse, when this feature is used, polling happens right away, and the polling timer is reset. Afterwards the person activating this is optionally notified if polling was successful or not."

## Clarifications

### Session 2026-03-04

- Q: How is the on-demand poll trigger exposed? → A: Authenticated REST endpoint on the existing OpenAS2 admin/command API.
- Q: Which pollers should the trigger affect by default? → A: All configured polling modules that send outbound files.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trigger Poll Immediately and Reset Timer (Priority: P1)

An operator or external system needs to cause the server to poll the outbox for files to send without waiting for the next scheduled interval. When they invoke the trigger, the server runs the poll cycle immediately and resets the polling timer so the next scheduled poll occurs one full interval after this on-demand run.

**Why this priority**: This is the core value—immediate poll and timer reset—without which the feature has no purpose.

**Independent Test**: An operator invokes the exposed trigger; the server performs a poll cycle within a short time (e.g., within seconds), and the next scheduled poll occurs one configured interval after that run.

**Acceptance Scenarios**:

1. **Given** the server is running with polling enabled and an interval configured, **When** the operator invokes the on-demand poll trigger, **Then** the server runs the poll cycle (scan outbox, process eligible files) immediately.
2. **Given** the operator has just invoked the on-demand poll trigger, **When** the poll cycle completes, **Then** the next scheduled poll is scheduled one full interval from the completion (or start) of that run (timer reset).
3. **Given** the server is running, **When** the operator invokes the trigger from outside the application (e.g., via an exposed interface), **Then** the trigger is accepted and the poll runs without requiring a server restart or config change.

---

### User Story 2 - Optional Success/Failure Notification (Priority: P2)

The person who activated the on-demand poll may optionally be informed whether the poll run succeeded or failed (e.g., completed without error vs. one or more errors during the run). Notification is optional and may be configured or requested per call.

**Why this priority**: Improves operator confidence and troubleshooting; secondary to the ability to trigger and reset.

**Independent Test**: When notification is requested or enabled, the activator receives a clear indication of success or failure for that run; when not requested, no notification is required.

**Acceptance Scenarios**:

1. **Given** the operator has triggered an on-demand poll and has requested notification, **When** the poll run completes successfully, **Then** the activator is notified that polling was successful.
2. **Given** the operator has triggered an on-demand poll and has requested notification, **When** the poll run encounters one or more errors, **Then** the activator is notified that polling was not successful with a brief explanation of the error(s).
3. **Given** notification is optional, **When** the activator does not request notification, **Then** the system may complete the poll without sending any notification to the activator.

---

### Edge Cases

- What happens when the on-demand trigger is invoked while a poll cycle is already running (e.g., timer-triggered poll in progress)?
- What happens when the trigger is invoked by an unauthorized or unauthenticated caller (if the exposed interface is protected)?
- What happens when multiple trigger requests arrive in quick succession—does the server run one poll per request, queue, or coalesce?
- How does the system behave when the polling module or outbox is misconfigured or unavailable at the moment of trigger?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an exposed way (from outside the application process) for an operator or system to request an immediate poll for files to send.
- **FR-002**: When the on-demand trigger is invoked, the system MUST run the poll cycle (outbox scan and file processing) without waiting for the next scheduled interval.
- **FR-003**: After running the on-demand poll, the system MUST reset the polling timer so that the next scheduled poll occurs one full configured interval after the on-demand run (or after its completion, as defined by the timer-reset behavior).
- **FR-004**: The system MAY support optional notification to the activator indicating whether the poll run succeeded or failed; when supported, the activator MUST be able to request or configure this behavior.
- **FR-005**: The system MUST ensure that on-demand trigger invocation is safe with respect to concurrent timer-based polls (e.g., no undefined behavior, no duplicate processing of the same file in the same run).
- **FR-006**: The exposed trigger MUST be invocable without restarting the server or changing configuration files for that single invocation.

### Key Entities

- **Poll trigger request**: A request from outside the application to run the outbox poll immediately.
- **Poll run result**: The outcome of a single poll cycle (success, failure, or partial failure), used for optional notification.
- **Polling timer**: The scheduler that runs the poll at a configured interval; it must be reset after an on-demand run.

## Assumptions

- "Exposed from outside" means an authenticated REST endpoint on the existing OpenAS2 admin/command API that can be called by an operator or another system.
- By default, the on-demand trigger applies to all configured polling modules that send outbound files (i.e., it is a global “poll everything now” operation).
- "Timer reset" means the next scheduled poll is one full interval after the on-demand run (or after it starts), consistent with typical fixed-rate semantics.
- Optional notification may be implemented as a synchronous response to the trigger call and/or an asynchronous callback; the spec does not prescribe which.
- There may be one or more polling modules (e.g., per partnership or per outbox); the feature may apply to a single poller, all pollers, or a selected poller, depending on implementation; the requirement is that at least one poll can be triggered on demand.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can trigger an immediate poll from outside the application and observe that the poll runs within a short, bounded time (e.g., within seconds of the request).
- **SC-002**: After an on-demand poll, the next scheduled poll occurs at the expected time (one interval after the on-demand run), demonstrating correct timer reset.
- **SC-003**: When optional notification is used, the activator can determine from the notification whether the poll run succeeded or failed in 100% of cases.
- **SC-004**: Triggering on-demand poll does not cause duplicate sending of the same file in the same run or corrupt polling state.
