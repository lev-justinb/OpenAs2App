# Quickstart: Tool Launcher on File Receive

**Feature**: 361-tool-launcher  
**Date**: 2026-03-12

## Prerequisites

- OpenAS2 server built and running
- A CLI/EXE or script that accepts a file path as its first argument

## Enable the Tool Launcher

1. **Add properties** to `config.xml` (in the `<properties>` block) or `openas2.properties`:

   ```xml
   module.ToolLauncherModule.enabled="true"
   module.ToolLauncherModule.command="C:/path/to/your-tool.exe"
   ```

   Or use a relative path (relative to server working directory):

   ```xml
   module.ToolLauncherModule.command="./scripts/on-receive.sh"
   ```

2. **Add the module** to the processor in `config.xml`, **after** `MessageFileModule`:

   ```xml
   <module enabled="$properties.module.ToolLauncherModule.enabled$"
           classname="org.openas2.processor.tool.ToolLauncherModule"
           command="$properties.module.ToolLauncherModule.command$"/>
   ```

3. **Restart** the OpenAS2 server.

## Verify

1. Send a test AS2 message to the server.
2. After the file is stored, the tool should be invoked with the stored file path.
3. Check logs for `ToolLauncherModule` entries—success or failure will be logged.

## Disable

Set `module.ToolLauncherModule.enabled="false"` (or remove the properties and set `enabled="false"` on the module). Restart the server.

## Example Tool (Windows batch)

```batch
@echo off
REM Receives file path as %1
echo Received: %1
REM Your processing here
```

## Example Tool (Bash)

```bash
#!/bin/bash
# Receives file path as $1
echo "Received: $1"
# Your processing here
```

## Troubleshooting

- **Tool not running**: Ensure `enabled` is true, `command` is set, and the path is correct.
- **Path with spaces**: The tool receives the path as a single argument; no quoting needed in your script.
- **Tool fails**: Check server logs; AS2 receive and MDN are unaffected.
