# Poll Trigger Filename Filter — Design Spec

**Date:** 2026-03-23
**Status:** Approved

## Overview

Extend the existing `POST /api/poll/trigger` REST endpoint to accept an optional JSON body `{"file": "1.edi"}`. When a filename is supplied, only that file is sent (across all outbox directories). When no body is supplied, the existing full-poll behavior is unchanged.

## Requirements

- `POST /api/poll/trigger` with no body → existing behavior (poll all outboxes, send all pending files)
- `POST /api/poll/trigger` with `Content-Type: application/json` body `{"file": "1.edi"}` → only send the file named `1.edi`
- All polling modules (all outbox directories) are checked for the filename
- If the file is found and sent in one or more outboxes → return `SENT` result
- If the filename is not found in any outbox → return a distinct `NOT_FOUND` result (new)
- Existing callers sending no body or `application/x-www-form-urlencoded` are unaffected

## Changes

### 1. `CommandResult.java`

Add one constant:

```java
public static final String TYPE_NOT_FOUND = "NOT_FOUND";
```

### 2. `ApiResource.java`

Add a dedicated endpoint method annotated `@Consumes(MediaType.APPLICATION_JSON)` for the `poll/trigger` path:

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

**Routing note:** Jersey gives literal paths higher priority than parameterized paths. The existing `postCommand` handler matches `/{resource}/{action}{id:...}` (parameterized); the new handler matches the literal path `poll/trigger`. Jersey selects the new handler for `POST /api/poll/trigger` regardless of `Content-Type`. The `@Consumes(APPLICATION_JSON)` annotation ensures a request with a non-JSON body (or no body) that reaches this method gets a `415`, preserving the existing `postCommand` as the fallback for `application/x-www-form-urlencoded` callers. Existing callers that send `Content-Type: application/x-www-form-urlencoded` continue to work via `postCommand` because the more-specific literal path does not match `@Consumes(APPLICATION_FORM_URLENCODED)` requests.

> **Note for callers:** Sending `POST /api/poll/trigger` with no body and no `Content-Type` header is not a supported calling convention and may result in a `415`. Callers not supplying a JSON body should continue to set `Content-Type: application/x-www-form-urlencoded`.

### 3. `DirectoryPollingModule.java`

Add a new public method `triggerFileNow` that directly processes a single named file, bypassing the normal scan/track mechanism:

```java
public synchronized boolean triggerFileNow(String filename) throws OpenAS2Exception {
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
- **Synchronization:** The method is `synchronized` on `this` to prevent interleaving with the scheduled `PollTask` (which also runs on the same instance). This is consistent with the guard already used in `PollingModule.triggerPollNow()`.
- **Filter bypass:** `triggerFileNow` intentionally bypasses the extension and filename-regex filters that `scanDirectory` applies. The caller supplies an exact filename and is trusted to supply a valid one. This is by design — the feature is an operator-level tool, not a general scanning path.

### 4. `TriggerPollCommand.java`

Branch in `execute(Object[] params)` based on whether a filename param was provided. The params array received by `execute` is the full params list passed to `feedCommand`, so `params[0]` is always `"trigger"` (the action name) and `params[1]` is the filename when present.

- **No filename** (`params.length < 2`) → existing path: call `triggerPollNow()` on all pollers
- **Filename present** (`params[1]` is a non-empty string) → new path:
  - For each poller: if poller is a `DirectoryPollingModule`, call `triggerFileNow(filename)` and track the result; pollers that are not `DirectoryPollingModule` are skipped (no outbox directory concept)
  - If no poller returned `true` → return `CommandResult(TYPE_NOT_FOUND, "File '<name>' not found in any outbox.")`
  - If at least one poller returned `true` → build and return a `SENT` result using the same result-building loop as the existing full-poll path: iterate pollers that found the file, collect `getLastPollSentFileNames()` / `getLastPollSentDetails()`, populate `outboxesChecked`, `allFiles`, `sentByOutbox` in the same structure

## Data Flow

```
POST /api/poll/trigger
Content-Type: application/json
{"file": "1.edi"}

  → ApiResource.triggerPollWithBody()
      → feedCommand("poll", ["trigger", "1.edi"])
          → TriggerPollCommand.execute(["trigger", "1.edi"])
              → filename = params[1] = "1.edi"
              → for each DirectoryPollingModule poller:
                    poller.triggerFileNow("1.edi")
                        → synchronized(this)
                        → checks outboxDir/1.edi exists
                        → if yes: processSingleFile(file) → returns true
                        → if no:  returns false
              → if any returned true: build SENT result (same structure as full-poll path)
              → if none returned true: return CommandResult(NOT_FOUND, "File '1.edi' not found in any outbox.")
```

## Response Examples

**File found and sent:**
```json
{
  "type": "SENT",
  "results": [
    "Poll completed for 1 poller(s).",
    "Outbox outbox1: sent 1.edi",
    { "poll": { "outboxesChecked": ["outbox1"], "allFiles": ["1.edi"], "sentByOutbox": [{ "outbox": "outbox1", "files": ["1.edi"] }] } }
  ]
}
```

The result structure is identical to the full-poll `SENT` response. The same result-building loop is reused: only pollers that returned `true` from `triggerFileNow` are included in `outboxesChecked` and `sentByOutbox`.

**File not found:**
```json
{
  "type": "NOT_FOUND",
  "results": ["File '1.edi' not found in any outbox."]
}
```

**No body / form-encoded (existing behavior unchanged):**
```json
{
  "type": "OK",
  "results": ["Poll completed for 2 poller(s).", "No files sent.", { "poll": { "outboxesChecked": ["outbox1", "outbox2"], "allFiles": [], "sentByOutbox": [] } }]
}
```

## Testing

**`TriggerPollCommandTest`** — add:
- Filename param provided (`params[1]` set), one `DirectoryPollingModule` poller returns `true` from `triggerFileNow` → result type is `SENT`, filename appears in results
- Filename param provided, no poller finds the file → result type is `NOT_FOUND`, message contains the filename
- Filename param provided, poller is a plain `PollingModule` (not `DirectoryPollingModule`) → skipped, result is `NOT_FOUND`
- Existing tests (no filename) remain unchanged

**`DirectoryPollingModule` unit tests** — add for `triggerFileNow`:
- File exists in outbox → returns `true`, `getLastPollSentFileNames()` contains the filename
- File does not exist → returns `false`

## Out of Scope

- Glob/pattern matching (only exact filename match)
- Filtering by outbox/partnership — all outboxes are always checked
- Timer reset on targeted send
- Applying the configured extension/regex filters in `triggerFileNow` (bypass is intentional)
