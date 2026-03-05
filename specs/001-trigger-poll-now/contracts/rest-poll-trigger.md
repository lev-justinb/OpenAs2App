# REST API Contract: On-Demand Poll Trigger

**Feature**: 001-trigger-poll-now  
**Base path**: Same as existing API (e.g. `http://localhost:8080/api` when REST is enabled).

## Endpoint

### Trigger poll now

- **Method**: `POST` or `GET`
- **Path**: `/api/poll/trigger`  
  (Routed as resource `poll`, action `trigger` via existing `ApiResource` pattern `/{resource}/{action}`.)
- **Authentication**: Same as existing admin API (e.g. HTTP Basic; credentials from `restapi.command.processor.userid` / `password`). Role: `ADMIN`.
- **Request**: No required body. Optional query or form parameters may be used in implementation to request detailed result (e.g. `includeResult=true`).
- **Response**:
  - **Status**: `200 OK` (success) or `5xx` / exception (e.g. server error).
  - **Body**: JSON-serialized `CommandResult` (consistent with other commands):
    - **type**: `"OK"` or `"ERROR"`.
    - **results**: List of strings. On success: first entry is a summary (e.g. `"Poll completed for N poller(s)."`). If any poller sent files, subsequent entries list files sent per outbox, e.g. `"Outbox /path/to/outbox: sent file1.edi, file2.edi"`. If no files were sent, a second entry may be `"No files sent."`. On error, a single message describing the failure.
  - **Content-Type**: `application/json`.

### Example (success)

**Request**

```http
POST /api/poll/trigger HTTP/1.1
Host: localhost:8080
Authorization: Basic dXNlcklEOpB3ZA==
Content-Type: application/x-www-form-urlencoded
```

**Response**

```json
{
  "type": "OK",
  "results": ["Poll completed for 2 poller(s).", "No files sent."]
}
```

When one or more pollers send files, the response may include per-outbox file lists:

```json
{
  "type": "OK",
  "results": [
    "Poll completed for 1 poller(s).",
    "Outbox /path/to/outbox: sent file1.edi, file2.edi"
  ]
}
```

### Example (failure)

**Response** (e.g. no pollers configured or exception during poll)

```json
{
  "type": "ERROR",
  "results": ["Poll failed: &lt;brief reason&gt;"]
}
```

## Compatibility

- Uses existing `CommandResult` and `ApiResource` serialization; no new response schema.
- Unauthorized callers receive the same 401/403 behavior as other admin endpoints.
