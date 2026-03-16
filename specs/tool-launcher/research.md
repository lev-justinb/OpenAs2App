# Research: Tool Launcher on File Receive

**Feature**: tool-launcher  
**Date**: 2026-03-12

## 1. Async vs Sync Tool Execution

**Decision**: Async (fire-and-forget)

**Rationale**:
- Spec FR-006: "The system MUST NOT block or fail the receive or MDN flow if the tool launcher fails or is misconfigured."
- Blocking on the tool would delay MDN response and could cause sender timeouts.
- Async execution ensures receive and MDN complete immediately; tool runs in background.
- JDK `ProcessBuilder.start()` returns immediately; process runs independently.

**Alternatives considered**:
- Sync: Rejected—would block receive thread and risk MDN delays.
- Configurable sync/async: Rejected—adds complexity; async is the safe default per spec.

---

## 2. Tool Invocation Mechanism (JDK)

**Decision**: Use `java.lang.ProcessBuilder` (JDK, no new dependency)

**Rationale**:
- Constitution V: No gratuitous new libraries.
- ProcessBuilder is standard JDK since Java 5; supports command + args, env, working dir.
- Handles path with spaces when args are passed as separate strings.
- No need for Apache Commons Exec or similar.

**Alternatives considered**:
- Apache Commons Exec: Rejected—new dependency.
- Runtime.exec(): Rejected—deprecated; ProcessBuilder is preferred.

---

## 3. Stored File Path Resolution

**Decision**: Resolve path using same template as MessageFileModule (ParameterParser + MessageParameters)

**Rationale**:
- Options contain `Partnership.PA_STORE_RECEIVED_FILE_TO` (template string).
- MessageFileModule resolves via `CompositeParameters` (date, msg, rand) and `ParameterParser.parse()`.
- ToolLauncherModule will use identical logic to compute the stored file path.
- Ensures we invoke the tool with the correct path where the file was written.

**Alternatives considered**:
- Modify MessageFileModule to pass path in options: Rejected—unnecessary change to existing module.
- Pass message payload path: Rejected—payload may be in memory; we need the disk path.

---

## 4. Tool Arguments (What the CLI/EXE Receives)

**Decision**: Pass stored file path as first argument; support optional additional args via config

**Rationale**:
- Spec: "The external tool receives the stored file path (and optionally message metadata)."
- Most tools expect: `tool.exe /path/to/file.edi`
- Optional: config param for extra args (e.g., sender ID) using parameter substitution.
- Keeps MVP simple: file path only; metadata can be added later if needed.

**Alternatives considered**:
- Environment variables only: Rejected—less portable; args are standard for CLIs.
- Full metadata as JSON file: Rejected—overkill for MVP.

---

## 5. Concurrent Receives and Tool Instances

**Decision**: Launch one tool process per received file; no queuing or concurrency limit

**Rationale**:
- Spec does not require limiting concurrency.
- Each receive is independent; tool runs in its own process.
- OS manages process limits; no need to add application-level throttling for MVP.
- If operators need limits, they can configure a wrapper script that queues.

**Alternatives considered**:
- Single-threaded queue: Rejected—adds complexity; not in spec.
- Configurable max concurrent: Deferred—can add if operators request it.

---

## 6. Executable Not Found / Tool Failure Handling

**Decision**: Log error; do not throw; AS2 transaction completes successfully

**Rationale**:
- Spec FR-006: Tool launcher failures must not cause receive or MDN failures.
- Catch all exceptions in ToolLauncherModule.handle(); log with message ID and path.
- Return normally so Processor continues; MDN is sent as usual.

**Alternatives considered**:
- Retry on failure: Rejected—spec says best-effort; no retry required.
- Move file to error dir: Rejected—file was already stored; tool failure is separate concern.

---

## 7. Config Placement and Properties

**Decision**: Follow existing pattern—properties in config.xml, module in processor

**Rationale**:
- Existing: `module.MessageFileModule.enabled`, `module.MessageFileModule.filename`
- New: `module.ToolLauncherModule.enabled` (default false), `module.ToolLauncherModule.command`
- Module placed after MessageFileModule in processor module list.
- Properties can use `$properties.X$` for central override via openas2.properties.

**Alternatives considered**:
- Partnership-level config: Deferred—global config sufficient for MVP.
- Separate config file: Rejected—constitution requires compatibility with existing config.
