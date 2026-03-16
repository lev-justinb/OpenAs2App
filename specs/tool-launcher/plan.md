# Implementation Plan: Tool Launcher on File Receive

**Branch**: `tool-launcher` | **Date**: 2026-03-12 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/tool-launcher/spec.md`

## Summary

Add an optional tool launcher that invokes an external CLI/EXE when an AS2 file is successfully received and stored. The feature is disabled by default and configured via existing config.xml properties. Implementation adds a new `ToolLauncherModule` as a `ProcessorModule` that handles `DO_STORE` (placed after `MessageFileModule` in the processor chain), uses JDK `ProcessBuilder` for execution (no new dependencies), and runs asynchronously so tool failures never block receive or MDN flow.

## Technical Context

**Language/Version**: Java 11+ (per README and constitution)  
**Primary Dependencies**: Existing stack only—Jersey, Grizzly, Slf4j, OpenAS2 processor/session. No new libraries; uses JDK `ProcessBuilder`.  
**Storage**: N/A (reads stored file path from message/options; no new persistence).  
**Testing**: JUnit; existing patterns in `Server/src/test/java`.  
**Target Platform**: JVM (Linux/Windows/server), same as current OpenAS2 server.  
**Project Type**: Web service (AS2 server with processor modules).  
**Performance Goals**: Tool launch must not block receive; async fire-and-forget.  
**Constraints**: No new dependencies (constitution V); tool failures must not affect AS2 transaction or MDN.  
**Scale/Scope**: Single server; one tool invocation per successfully stored received file.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. AS2 Protocol Correctness & Interoperability**: No change to AS2 message handling, headers, or MDNs. Tool runs after storage, before MDN send. Pass.
- **II. Message Integrity, Reliability & Traceability**: No change to message flow or persistence; tool is best-effort post-processing. Pass.
- **III. Security by Default**: Tool path is configurable; operators control what runs. No secrets in logs. Pass.
- **IV. Operational Observability & Diagnosability**: Tool launch and failures will be logged; no payload in logs. Pass.
- **V. Dependency Minimalism**: No new dependencies; uses JDK `ProcessBuilder` only. Pass.
- **System Constraints**: Java 11+, Maven build, file-based config unchanged. Pass.
- **Development Workflow**: Tests and documentation updates will be included per constitution. Pass.

## Project Structure

### Documentation (this feature)

```text
specs/361-tool-launcher/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1 (config contract)
└── tasks.md             # From /speckit.tasks
```

### Source Code (repository root)

```text
Server/
├── src/main/java/org/openas2/processor/
│   └── tool/                        # New: tool launcher
│       └── ToolLauncherModule.java
├── src/config/
│   └── config.xml                   # Add ToolLauncherModule, properties
└── src/test/java/org/openas2/processor/tool/
    └── ToolLauncherModuleTest.java  # Unit tests
```

**Structure Decision**: Single-module Server project. New `ToolLauncherModule` under `org.openas2.processor.tool`; placed after `MessageFileModule` in processor chain so it runs after storage. Config follows existing `module.X.enabled` and `module.X.param` patterns.

## Complexity Tracking

No constitution violations. Table left empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
