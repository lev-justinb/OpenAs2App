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
    - **results**: List of strings (e.g. one entry like `"Poll completed"` or `"Poll completed for N poller(s)"`; on error, message describing failure).
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
  "results": ["Poll completed for 2 poller(s)."]
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
