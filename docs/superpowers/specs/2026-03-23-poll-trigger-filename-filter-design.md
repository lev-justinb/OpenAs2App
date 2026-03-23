# Poll Trigger Filename Filter — Design Spec

**Date:** 2026-03-23
**Status:** Approved

## Overview

Extend the existing `POST /api/poll/trigger` REST endpoint to accept an optional JSON body `{"file": "1.edi"}`. When a filename is supplied, only that file is sent (across all outbox directories). When no body is supplied, the existing full-poll behavior is unchanged.

## Requirements

- `POST /api/poll/trigger` with no body → existing behavior (poll all outboxes, send all pending files)
- `POST /api/poll/trigger` with `Content-Type: application/json` body `{"file": "1.edi"}` → only send the file named `1.edi`
- All polling modules (all outbox directories) are checked for the filename
- If the file is found and sent in one or more outboxes → return `SENT` result (existing behavior)
- If the filename is not found in any outbox → return a distinct `NOT_FOUND` result (new)
- Existing callers sending no body or `application/x-www-form-urlencoded` are unaffected

## Changes

### 1. `CommandResult.java`

Add one constant:

```java
public static final String TYPE_NOT_FOUND = "NOT_FOUND";
```

### 2. `ApiResource.java`

Add a dedicated endpoint method that consumes `application/json` for the `poll/trigger` path. Jersey disambiguates by `Content-Type`, so the existing `postCommand` handler (which consumes `application/x-www-form-urlencoded`) continues to serve callers that send no body.

```java
@RolesAllowed({"ADMIN"})
@POST
@Path("poll/trigger")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public Response triggerPollWithBody(Map<String, String> body) throws Exception {
    List<String> params = new ArrayList<>();
    params.add("trigger");
    if (body != null && body.containsKey("file")) {
        params.add(body.get("file"));
    }
    CommandResult output = getProcessor().feedCommand("poll", params);
    String jsonResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
    return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
}
```

### 3. `DirectoryPollingModule.java`

Add a new public method `triggerFileNow` that bypasses the normal scan/track mechanism and directly processes a single named file:

```java
public boolean triggerFileNow(String filename) throws OpenAS2Exception {
    File file = new File(getOutboxDir(), filename);
    if (!file.exists() || !file.isFile()) {
        return false;
    }
    lastPollSentFileNames.clear();
    lastPollSentDetails.clear();
    processSingleFile(file, file.getAbsolutePath());
    return true;
}
```

- Returns `true` if the file was found and processing was attempted; `false` if not found in this outbox.
- Does **not** reset the polling timer — the scheduled poll continues on its normal interval.
- Clears `lastPollSentFileNames` / `lastPollSentDetails` before processing so results reflect only this targeted send.

### 4. `TriggerPollCommand.java`

Branch in `execute(Object[] params)` based on whether a filename param was provided:

- **No filename** (`params` empty or null) → existing path: call `triggerPollNow()` on all pollers
- **Filename present** (`params[0]` is a non-empty string) → new path:
  - For each poller: if poller is a `DirectoryPollingModule`, call `triggerFileNow(filename)`; track whether any returned `true`
  - If no poller found the file → return `CommandResult(TYPE_NOT_FOUND, "File '<name>' not found in any outbox.")`
  - If at least one poller found and sent the file → return `SENT` result (same structure as existing full-poll result)

Pollers that are not `DirectoryPollingModule` instances are skipped in filename-filter mode (they have no outbox directory concept).

## Data Flow

```
POST /api/poll/trigger
Content-Type: application/json
{"file": "1.edi"}

  → ApiResource.triggerPollWithBody()
      → feedCommand("poll", ["trigger", "1.edi"])
          → TriggerPollCommand.execute(["1.edi"])
              → for each DirectoryPollingModule poller:
                    poller.triggerFileNow("1.edi")
                        → checks outboxDir/1.edi exists
                        → if yes: processSingleFile(file) → returns true
                        → if no:  returns false
              → if any found: return CommandResult(SENT, ...)
              → if none found: return CommandResult(NOT_FOUND, "File '1.edi' not found in any outbox.")
```

## Response Examples

**File found and sent:**
```json
{
  "type": "SENT",
  "results": [
    "Poll completed for 1 poller(s).",
    "Outbox outbox1: sent 1.edi",
    { "poll": { "outboxesChecked": ["outbox1"], "allFiles": ["1.edi"], "sentByOutbox": [...] } }
  ]
}
```

**File not found:**
```json
{
  "type": "NOT_FOUND",
  "results": ["File '1.edi' not found in any outbox."]
}
```

**No body (existing behavior unchanged):**
```json
{
  "type": "OK",
  "results": ["Poll completed for 2 poller(s).", "No files sent.", { "poll": { ... } }]
}
```

## Testing

Update `TriggerPollCommandTest` with:
- Test: filename param provided, one poller finds and sends the file → `SENT`
- Test: filename param provided, no poller finds the file → `NOT_FOUND`
- Test: filename param provided, poller is not a `DirectoryPollingModule` → `NOT_FOUND` (skipped)
- Existing tests remain unchanged (no regression)

`DirectoryPollingModule` unit test for `triggerFileNow`:
- File exists in outbox → returns `true`, `lastPollSentFileNames` populated
- File does not exist → returns `false`

## Out of Scope

- Glob/pattern matching (only exact filename match)
- Filtering by outbox/partnership — all outboxes are always checked
- Timer reset on targeted send
