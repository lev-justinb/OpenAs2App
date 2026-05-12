# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenAS2 is an AS2 (Applicability Statement 2) protocol server for secure B2B document transfer (EDI-X12, EDIFACT, XML, binary). This is a fork (`lev-justinb`) that adds two capabilities on top of upstream:
1. **REST API poll trigger** — `POST /api/poll/trigger` forces immediate polling of outbound directories
2. **Tool Launcher Module** — executes an external CLI/EXE after each successfully received file

## Build & Test Commands

```bash
# Build and package
./mvnw clean package

# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=TriggerPollCommandTest

# Run a specific test method
./mvnw test -Dtest=TriggerPollCommandTest#testMethodName

# Check dependency updates
./mvnw versions:display-dependency-updates
```

Java 11+ is required.

## Architecture

The application is **configuration-driven**: all components (modules, commands, partnerships) are declared in XML and dynamically instantiated at startup.

### Startup Flow

`OpenAS2Server.main()` → `XMLSession` loads `config.xml` → components instantiated → `DefaultProcessor` starts all modules → REST API server starts (if enabled)

### Key Layers

| Layer | Key Classes | Description |
|-------|-------------|-------------|
| Session | `XMLSession`, `BaseSession` | Loads XML config, holds references to all factories and the processor |
| Processor | `DefaultProcessor` | Orchestrates all modules; passes messages between them |
| Receiver Modules | `AS2ReceiverModule` | Accepts inbound AS2 messages via HTTP/HTTPS (ports 10080/10443) |
| Polling Modules | `AS2DirectoryPollingModule`, `DirectoryPollingModule` | Watch outbound directories and send files to partners |
| Sender | `AS2SenderModule` | Sends AS2 messages to trading partners |
| Storage | `MessageFileModule` | Persists received messages to filesystem |
| Tracking | `DbTrackingModule` | Records message metadata in embedded H2 database |
| Tool Launcher | `ToolLauncherModule` | Fork addition — runs external command after file receipt |
| REST API | `RestCommandProcessor`, `ApiResource` | Jersey/Grizzly HTTP server on port 8080; basic auth |
| Commands | `TriggerPollCommand`, command registry | Command pattern; loaded from `commands.xml` |

### Inbound Message Flow
```
HTTP/S → AS2ReceiverModule → DefaultProcessor → MessageFileModule → ToolLauncherModule
```

### Outbound Message Flow
```
Poll trigger (timer or REST API) → DirectoryPollingModule → AS2SenderModule → Partner
```

### Configuration Files (under `Server/src/config/`)
- `config.xml` — main config: properties, modules, command processors
- `commands.xml` — command definitions for the command framework
- `partnerships.xml` — per-partner AS2 IDs and polling directories
- `logback.xml` — SLF4J/Logback logging configuration

Container deployments use `OPENAS2PROP_` environment variables (double-underscore `__` maps to `.`) to override properties without editing `config.xml`.

## Fork-Specific Code

The two additions live in:
- `Server/src/main/java/org/openas2/cmd/processor/restapi/` — REST API including `TriggerPollCommand`
- `Server/src/main/java/org/openas2/processor/receiver/` — `ToolLauncherModule`

The `TriggerPollCommand` iterates all active `DirectoryPollingModule` instances from the processor, calls `poll()` on each, and aggregates results into a `CommandResult` with type `SENT`, `OK`, or `ERROR`.

## Default Ports

| Service | Port |
|---------|------|
| AS2 Receiver (HTTP) | 10080 |
| AS2 Receiver (HTTPS) | 10443 |
| MDN Receiver (HTTP) | 10081 |
| MDN Receiver (HTTPS) | 10444 |
| REST API | 8080 |
| Health Check | 10099 |
