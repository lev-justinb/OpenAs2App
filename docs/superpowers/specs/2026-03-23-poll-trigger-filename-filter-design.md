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

### 2. `PollingModule.java`

Change `isBusy()` and `setBusy()` from `private` to `protected` so that `DirectoryPollingModule.triggerFileNow` can use the busy-flag mechanism:

```java
protected boolean isBusy() { return busy; }
protected void setBusy(boolean b) { busy = b; }
```

### 3. `ApiResource.java`

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

**Routing:** Jersey gives literal paths higher priority than parameterized paths. The existing `postCommand` handler matches `/{resource}/{action}{id:...}` (parameterized); the new handler matches the literal path `poll/trigger`. Jersey selects the new handler for all `POST /api/poll/trigger` requests.

**Caller behavior for edge cases:**
- `Content-Type: application/json` with body `{}` or null body → `body` has no `"file"` key → same as no-body call → full poll (no error)
- `Content-Type: application/x-www-form-urlencoded` → falls through to the existing `postCommand` handler (literal path with `@Consumes(APPLICATION_JSON)` rejects non-JSON requests, Jersey falls back to the parameterized handler which consumes form-encoded)
- No body, no `Content-Type` header → not a supported calling convention; behavior is undefined. Existing callers should continue to set `Content-Type: application/x-www-form-urlencoded`.

### 4. `DirectoryPollingModule.java`

Add a new public method `triggerFileNow` that directly processes a single named file, bypassing the normal scan/track mechanism:

```java
public boolean triggerFileNow(String filename) throws OpenAS2Exception {
    File file = new File(getOutboxDir(), filename);
    if (!file.exists() || !file.isFile()) {
        return false;
    }
    synchronized (this) {
        if (isBusy()) {
            return false;
        }
        setBusy(true);
    }
    try {
        lastPollSentFileNames.clear();
        lastPollSentDetails.clear();
        processSingleFile(file, file.getAbsolutePath());
    } finally {
        setBusy(false);
    }
    return true;
}
```

- Returns `true` if the file was found and processing was attempted; `false` if not found in this outbox or if the poller is currently busy.
- **Synchronization:** Uses the same `busy` flag that `PollTask.run()` checks before running. Setting `busy=true` prevents the scheduled `PollTask` from firing concurrently during the targeted send. The `isBusy()` check and `setBusy(true)` are done inside a `synchronized(this)` block to prevent a race with `triggerPollNow` (which is also `synchronized`). This is consistent with the guard in `PollingModule.triggerPollNow()`.
- Does **not** reset the polling timer — the scheduled poll continues on its normal interval after the busy flag is released.
- Clears `lastPollSentFileNames` / `lastPollSentDetails` before processing so results reflect only this targeted send.
- **Processing failure contract:** `triggerFileNow` returns `true` when the file exists and processing is attempted, not when it succeeds. This matches the contract of the existing full-poll path: `processSingleFile` catches `OpenAS2Exception` internally and archives the file to the error directory. If processing fails, `lastPollSentFileNames` will be empty despite `true` being returned. `TriggerPollCommand` must check `getLastPollSentFileNames()` after a `true` return before including that outbox in `sentByOutbox` — same as the existing full-poll result-building loop already does.
- **Filter bypass:** `triggerFileNow` intentionally bypasses the extension and filename-regex filters that `scanDirectory` applies. The caller supplies an exact filename and is trusted to supply a valid one. This is by design — the feature is an operator-level tool, not a general scanning path.

### 5. `TriggerPollCommand.java`

Branch in `execute(Object[] params)` based on whether a filename param was provided. `params` contains the full params list from `feedCommand`; the filename, when present, is at index 1 (index 0 is the action name, which `execute` does not use).

- **No filename** (`params.length < 2`) → existing path: call `triggerPollNow()` on all pollers
- **Filename present** (`params[1]` is a non-empty string) → new path:
  - For each poller: if poller is a `DirectoryPollingModule`, call `triggerFileNow(filename)` and track the result; pollers that are not `DirectoryPollingModule` are skipped (no outbox directory concept)
  - If no poller returned `true` → return `CommandResult(TYPE_NOT_FOUND, "File '<name>' not found in any outbox.")`
  - If at least one poller returned `true` → build and return a `SENT` result using the same result-building loop as the existing full-poll path: iterate pollers that found the file, call `getLastPollSentFileNames()` / `getLastPollSentDetails()`, populate `outboxesChecked`, `allFiles`, `sentByOutbox` in the same structure. Only outboxes with non-empty `getLastPollSentFileNames()` are included in `sentByOutbox`.

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
                        → checks outboxDir/1.edi exists
                        → if busy: returns false
                        → sets busy=true
                        → processSingleFile(file)
                        → sets busy=false
                        → returns true if file existed
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

The result structure is identical to the full-poll `SENT` response. The same result-building loop is reused.

**File not found (or poller busy for all outboxes):**
```json
{
  "type": "NOT_FOUND",
  "results": ["File '1.edi' not found in any outbox."]
}
```

**No body / empty JSON body `{}` / form-encoded (existing behavior):**
```json
{
  "type": "OK",
  "results": ["Poll completed for 2 poller(s).", "No files sent.", { "poll": { "outboxesChecked": ["outbox1", "outbox2"], "allFiles": [], "sentByOutbox": [] } }]
}
```

## Testing

**`TriggerPollCommandTest`** — add:
- Filename param at index 1, one `DirectoryPollingModule` mock returns `true` from `triggerFileNow` and returns `["1.edi"]` from `getLastPollSentFileNames()` → result type is `SENT`, filename appears in results
- Filename param at index 1, all `DirectoryPollingModule` pollers return `false` → result type is `NOT_FOUND`, message contains the filename
- Filename param at index 1, poller is a plain `PollingModule` (not `DirectoryPollingModule`) → skipped, result is `NOT_FOUND`
- Existing tests with no filename param remain unchanged

**`DirectoryPollingModule` unit tests** — add for `triggerFileNow`. Since `DirectoryPollingModule` is abstract, tests must use a concrete subclass (e.g., a minimal test double that implements `createMessage()`):
- File exists in outbox → returns `true`, `getLastPollSentFileNames()` contains the filename
- File does not exist → returns `false`
- Poller is busy when `triggerFileNow` is called → returns `false` without processing

## Files Changed

1. `CommandResult.java` — add `TYPE_NOT_FOUND` constant
2. `PollingModule.java` — promote `isBusy()` / `setBusy()` to `protected`
3. `ApiResource.java` — add `triggerPollWithBody` method
4. `DirectoryPollingModule.java` — add `triggerFileNow` method
5. `TriggerPollCommand.java` — add filename-filter branch in `execute`
6. `TriggerPollCommandTest.java` — add new test cases
7. `DirectoryPollingModuleTest.java` (new or existing) — add `triggerFileNow` tests

## Out of Scope

- Glob/pattern matching (only exact filename match)
- Filtering by outbox/partnership — all outboxes are always checked
- Timer reset on targeted send
- Applying the configured extension/regex filters in `triggerFileNow` (bypass is intentional)
