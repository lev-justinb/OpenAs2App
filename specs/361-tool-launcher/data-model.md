# Data Model: Tool Launcher on File Receive

**Feature**: 361-tool-launcher  
**Date**: 2026-03-12

## Overview

This feature does not introduce new persistent data. It uses existing message and options structures. The data model describes the configuration and runtime context used by the tool launcher.

## Configuration (Runtime)

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `enabled` | boolean | No | `false` | Whether the tool launcher is active. |
| `command` | string | Yes (when enabled) | — | Path to the CLI/EXE to execute. Absolute or relative to working directory. |

**Validation**:
- When `enabled` is true, `command` must be non-empty.
- When `enabled` is false, `command` is ignored.

## Runtime Context (Per Invocation)

| Field | Source | Description |
|-------|--------|-------------|
| `filePath` | Resolved from options | Absolute path to the stored file. Resolved via `PA_STORE_RECEIVED_FILE_TO` template and `ParameterParser`. |
| `message` | `Message` | AS2 message for context (sender, receiver, message-id). Used for logging, not passed to tool in MVP. |

## State Transitions

None. The tool launcher is stateless; each receive triggers an independent invocation.

## Relationships

- **ToolLauncherModule** → **Processor**: Registered as a processor module handling `DO_STORE`.
- **ToolLauncherModule** → **MessageFileModule**: Runs after MessageFileModule (order in processor chain); file is already on disk.
- **ToolLauncherModule** → **Options**: Reads `PA_STORE_RECEIVED_FILE_TO` to resolve file path.

## Notes

- No database or file storage for tool launcher state.
- Tool execution is fire-and-forget; no tracking of tool success/failure beyond logging.
