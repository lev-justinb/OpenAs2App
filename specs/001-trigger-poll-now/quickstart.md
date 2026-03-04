# Quickstart: On-Demand Poll Trigger

**Feature**: 001-trigger-poll-now

## Prerequisites

- OpenAS2 server running with at least one outbound directory poller configured (e.g. partnership pollers and/or a processor-mounted `AS2DirectoryPollingModule`).
- REST API enabled (`restapi.command.processor.enabled=true` in config) and base URI known (e.g. `http://localhost:8080`).
- Valid admin credentials (e.g. `restapi.command.processor.userid` / `password`).

## Trigger a poll now

1. **HTTP request**

   Send a POST (or GET) to the poll-trigger endpoint with admin auth:

   ```bash
   curl -X POST -u "userID:pWd" "http://localhost:8080/api/poll/trigger"
   ```

   Or with Basic auth header:

   ```bash
   curl -X POST -H "Authorization: Basic $(echo -n 'userID:pWd' | base64)" "http://localhost:8080/api/poll/trigger"
   ```

2. **Response**

   - Success: HTTP 200, body e.g. `{"type":"OK","results":["Poll completed for N poller(s)."]}`.
   - Failure: HTTP 200 with `"type":"ERROR"` and message in `results`, or 5xx on server error.

3. **Effect**

   - All configured outbound pollers run their poll cycle immediately.
   - Each poller’s timer is reset; the next scheduled poll is one full interval after this run.
   - No server restart or config change required.

## Optional notification

If the implementation supports a parameter to include a brief result in the response (e.g. `includeResult=true`), use it in the request; the same response body carries success or failure and a short message.

## Troubleshooting

- **401 Unauthorized**: Use the same userid/password as configured for the REST API.
- **404 or “command not found”**: Ensure the `poll` command with `trigger` action is registered in `commands.xml` and the server was restarted after adding it.
- **Empty or zero pollers**: Response may still be OK; “0 poller(s)” means no outbound directory pollers are configured (check partnerships and processor modules).
