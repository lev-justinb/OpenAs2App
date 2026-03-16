# Tasks: Tool Launcher on File Receive

**Input**: Design documents from `specs/361-tool-launcher/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Server**: `Server/src/main/java/` for source, `Server/src/config/` for config
- **Tests**: `Server/src/test/java/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add configuration properties for the tool launcher module

- [x] T001 Add module.ToolLauncherModule.enabled and module.ToolLauncherModule.command to config.xml properties block in Server/src/config/config.xml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the module skeleton and register it in the processor chain

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Create ToolLauncherModule class with init, canHandle, getModuleAction in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java
- [x] T003 Add ToolLauncherModule module element to processor in Server/src/config/config.xml after MessageFileModule

**Checkpoint**: Module registered; handle() implementation can begin

---

## Phase 3: User Story 1 - Launch External Tool on Successful Receive (Priority: P1) 🎯 MVP

**Goal**: When enabled and configured, the system invokes the external CLI/EXE after a file is successfully received and stored.

**Independent Test**: Enable tool launcher, configure executable path, send test AS2 message; verify tool process is started with stored file path.

### Implementation for User Story 1

- [x] T004 [US1] Implement handle() in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java: resolve stored file path from options (PA_STORE_RECEIVED_FILE_TO) using ParameterParser, CompositeParameters, MessageParameters, DateParameters, RandomParameters
- [x] T005 [US1] Implement async tool launch in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java: use ProcessBuilder with command and file path as first argument, call start() (fire-and-forget)
- [x] T006 [US1] Add error handling in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java: catch all exceptions in handle(), log with message ID and path, do not throw (per FR-006)

**Checkpoint**: User Story 1 complete—tool launches on receive when enabled

---

## Phase 4: User Story 2 - Disabled by Default (Priority: P1)

**Goal**: No external process is started unless the feature is explicitly enabled.

**Independent Test**: With default config (enabled=false), receive AS2 file; verify no external process is started.

### Implementation for User Story 2

- [x] T007 [US2] Ensure module.ToolLauncherModule.enabled defaults to "false" in Server/src/config/config.xml properties
- [x] T008 [US2] In ToolLauncherModule.init(), skip tool invocation when enabled is false; in canHandle() return false when disabled in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java

**Checkpoint**: User Story 2 complete—default config does not launch tools

---

## Phase 5: User Story 3 - Configurable Executable Path (Priority: P1)

**Goal**: Operators can specify the CLI/EXE path in config; the system uses it when invoking the tool.

**Independent Test**: Set different command paths in config, receive files; verify correct executable is invoked.

### Implementation for User Story 3

- [x] T009 [US3] In ToolLauncherModule.init(), read command parameter from config; log warning when enabled but command empty in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java
- [x] T010 [US3] Use configured command in ProcessBuilder in Server/src/main/java/org/openas2/processor/tool/ToolLauncherModule.java

**Checkpoint**: User Story 3 complete—command path is configurable

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Tests and documentation per constitution

- [x] T011 [P] Create ToolLauncherModuleTest in Server/src/test/java/org/openas2/processor/tool/ToolLauncherModuleTest.java with unit tests for enabled/disabled, path resolution, error handling
- [x] T012 Update README or docs with tool launcher configuration per specs/361-tool-launcher/quickstart.md
- [x] T013 Run quickstart.md validation: enable tool launcher, send test message, verify tool invoked

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies—can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1—blocks all user stories
- **Phase 3 (US1)**: Depends on Phase 2—core implementation
- **Phase 4 (US2)**: Depends on Phase 2—config default behavior
- **Phase 5 (US3)**: Depends on Phase 2, overlaps with Phase 3 (command used in handle)
- **Phase 6 (Polish)**: Depends on Phases 3–5

### User Story Dependencies

- **US1**: Depends on Phase 2; no dependency on US2/US3
- **US2**: Depends on Phase 2; config default
- **US3**: Depends on Phase 2; command param used in US1 handle()

### Within Each User Story

- T004 → T005 → T006 (path resolution before launch, launch before error handling)
- T007 and T008 can run in parallel
- T009 and T010: T009 (init) before T010 (use in handle)

### Parallel Opportunities

- T007 [US2] and T009 [US3] can run in parallel (different concerns)
- T011 (tests) can start after T006 complete

---

## Parallel Example: User Story 1

```bash
# Sequential for US1 (handle implementation):
T004: Resolve path
T005: ProcessBuilder async launch
T006: Error handling
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Enable tool launcher, send test message, verify tool runs
5. Deploy/demo if ready

### Incremental Delivery

1. Phase 1 + 2 → Module registered
2. Phase 3 (US1) → Tool launches when enabled
3. Phase 4 (US2) → Disabled by default verified
4. Phase 5 (US3) → Command configurable verified
5. Phase 6 → Tests and docs

### Suggested MVP Scope

Phases 1–3 deliver the core value: tool launches on receive when enabled. Phases 4–5 ensure correct defaults and config; Phase 6 adds tests and docs.

---

## Notes

- [P] tasks = different files or independent concerns
- [Story] label maps task to spec.md user stories
- ToolLauncherModule must support DO_STORE and run after MessageFileModule (processor order)
- Use JDK ProcessBuilder only—no new dependencies (constitution V)
- Async: ProcessBuilder.start() returns immediately; process runs in background
