# Config Contract: Tool Launcher Module

**Feature**: 361-tool-launcher  
**Date**: 2026-03-12

## Overview

The Tool Launcher is configured via the existing OpenAS2 config.xml and properties. No new config file or format is introduced.

## Properties (config.xml `<properties>` or openas2.properties)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `module.ToolLauncherModule.enabled` | boolean | `false` | Whether the tool launcher is active. When false, the module is not loaded. |
| `module.ToolLauncherModule.command` | string | — | Path to the CLI/EXE to execute. Required when enabled. Absolute or relative to server working directory. |

## Module Element (config.xml `<processor>`)

```xml
<module enabled="$properties.module.ToolLauncherModule.enabled$"
        classname="org.openas2.processor.tool.ToolLauncherModule"
        command="$properties.module.ToolLauncherModule.command$"/>
```

**Placement**: Must appear **after** `MessageFileModule` in the processor module list so the file is stored before the tool is invoked.

## Example

```xml
<!-- In properties block -->
module.ToolLauncherModule.enabled="false"
module.ToolLauncherModule.command=""
```

To enable:

```xml
module.ToolLauncherModule.enabled="true"
module.ToolLauncherModule.command="C:/tools/process-received.exe"
```

Or in openas2.properties:

```properties
module.ToolLauncherModule.enabled=true
module.ToolLauncherModule.command=C:/tools/process-received.exe
```

## Tool Invocation

When a file is successfully received and stored:
- The tool is invoked with the stored file path as the first argument.
- Example: `process-received.exe "C:/data/inbox/partner1/inbox/msg-12345.edi"`
- Paths with spaces are passed correctly (single argument).

## Validation

- If `enabled` is true and `command` is empty or missing, the module logs a warning at init and does not invoke the tool.
- If the executable is not found at runtime, the error is logged; the AS2 transaction completes successfully.
