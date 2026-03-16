# Feature Specification: Tool Launcher on File Receive

**Feature Branch**: `tool-launcher`  
**Created**: 2026-03-12  
**Status**: Draft  
**Input**: User description: "I want to add a tool launcher feature that starts an external cli/exe when a file is successfully received. this feature should be able to be enabled (disabled by default) with the ability to specify the cli/exe in the config"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Launch External Tool on Successful Receive (Priority: P1)

An operator enables the tool launcher and configures an executable path. When an AS2 file is successfully received and stored to disk, the system starts the configured CLI/EXE so the operator can process or react to incoming files automatically.

**Why this priority**: This is the core value—automatic invocation of an external tool when files arrive—without which the feature has no purpose.

**Independent Test**: An operator enables the tool launcher, configures an executable path, and sends a test AS2 message. The external tool is started after the file is stored. Can be verified by observing the tool process or its output.

**Acceptance Scenarios**:

1. **Given** the tool launcher is enabled and the executable path is configured, **When** an AS2 file is successfully received and stored, **Then** the system starts the configured external CLI/EXE.
2. **Given** the tool launcher is enabled and configured, **When** multiple files are received in succession, **Then** the system invokes the external tool for each successfully stored file.

---

### User Story 2 - Disabled by Default (Priority: P1)

The system does not launch any external tool unless the feature is explicitly enabled. Operators who do not configure the tool launcher experience no change in behavior.

**Why this priority**: Ensures backward compatibility and avoids unintended side effects for existing deployments.

**Independent Test**: With default or unmodified configuration, receive an AS2 file and verify no external process is started.

**Acceptance Scenarios**:

1. **Given** the tool launcher is not enabled (default), **When** a file is successfully received and stored, **Then** no external process is started.
2. **Given** the tool launcher is explicitly disabled, **When** a file is received, **Then** no external process is started.

---

### User Story 3 - Configurable Executable Path (Priority: P1)

An operator specifies the CLI/EXE path in configuration. The system uses this path when invoking the external tool. The path may be absolute or relative to a known base (e.g., installation directory).

**Why this priority**: Operators need to point to their specific tool; without configurable path, the feature is unusable.

**Independent Test**: Configure different executable paths, receive files, and verify the correct executable is invoked.

**Acceptance Scenarios**:

1. **Given** the tool launcher is enabled and the executable path is set in config, **When** a file is successfully received, **Then** the system invokes the configured executable.
2. **Given** the operator changes the executable path in config and restarts (or reloads config), **When** a file is received, **Then** the system invokes the new path.

---

### Edge Cases

- What happens when the configured executable does not exist or is not found?
- What happens when the external tool fails, returns an error, or hangs?
- How does the system handle concurrent receives—does it launch multiple tool instances, queue, or limit concurrency?
- How does the system handle an executable path that contains spaces or special characters?
- Does the tool run synchronously (blocking receive) or asynchronously (fire-and-forget)? What are the implications for receive throughput and MDN timing?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support an optional tool launcher that starts an external CLI/EXE when a file is successfully received and stored.
- **FR-002**: The tool launcher MUST be disabled by default.
- **FR-003**: The system MUST allow configuration to enable the tool launcher.
- **FR-004**: The system MUST allow configuration of the CLI/EXE path (command) to execute.
- **FR-005**: When enabled and configured, the system MUST invoke the external tool after successful file storage.
- **FR-006**: The system MUST NOT block or fail the receive or MDN flow if the tool launcher fails or is misconfigured.

### Key Entities

- **Tool launcher configuration**: Enable flag and executable path, stored in system configuration.
- **Received file context**: File path and message metadata (e.g., sender, receiver, message-id) available when the tool is invoked.

## Assumptions

- "Successfully received" means after the AS2 message is decrypted, verified, and stored to disk (storage module completes).
- Configuration follows existing system patterns (e.g., properties or module parameters for enabled flag and command path).
- The external tool receives the stored file path (and optionally message metadata) as arguments or environment; the exact mechanism is an implementation detail.
- Tool execution is best-effort; failures are logged but do not affect the AS2 transaction or MDN.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When the tool launcher is enabled and configured, 100% of successfully received and stored files trigger invocation of the external tool.
- **SC-002**: With default configuration, no external process is started when files are received.
- **SC-003**: Tool launcher failures (e.g., executable not found, tool crash) do not cause receive or MDN failures; the AS2 transaction completes successfully.
- **SC-004**: Operators can configure the executable path and observe that path being used when the tool is invoked.

