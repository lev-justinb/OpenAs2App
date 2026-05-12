# Poll Trigger Filename Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `POST /api/poll/trigger` to accept an optional JSON body `{"file": "1.edi"}` that restricts the poll to only that filename, with path-traversal rejection.

**Architecture:** Input validation in `TriggerPollCommand` (Layer 1: reject filenames with path characters), file processing in a new `DirectoryPollingModule.triggerFileNow` method (Layer 2: canonical path check), and a new JSON-consuming JAX-RS endpoint in `ApiResource` that routes JSON requests to the existing command machinery.

**Tech Stack:** Java 11, JUnit 5 (Jupiter), Mockito 5, Maven (`./mvnw`), Jersey (JAX-RS), Apache HttpClient (integration tests).

---

## File Map

| File | Change |
|------|--------|
| `Server/src/main/java/org/openas2/cmd/CommandResult.java` | Add `TYPE_NOT_FOUND` constant |
| `Server/src/main/java/org/openas2/processor/receiver/PollingModule.java` | Promote `isBusy()`/`setBusy()` to `protected` |
| `Server/src/main/java/org/openas2/processor/receiver/DirectoryPollingModule.java` | Add `triggerFileNow(String filename)` |
| `Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java` | Add filename-filter branch in `execute()` |
| `Server/src/main/java/org/openas2/cmd/processor/restapi/ApiResource.java` | Add `triggerPollWithBody()` endpoint |
| `Server/src/test/java/org/openas2/processor/receiver/DirectoryPollingModuleTest.java` | **New file** — unit tests for `triggerFileNow` |
| `Server/src/test/java/org/openas2/app/poll/TriggerPollCommandTest.java` | Add filename-filter test cases |
| `Server/src/test/java/org/openas2/app/RestApiTest.java` | Add JSON-body endpoint integration test |

---

## Task 1: Add `TYPE_NOT_FOUND` constant and expose busy flag

**Files:**
- Modify: `Server/src/main/java/org/openas2/cmd/CommandResult.java`
- Modify: `Server/src/main/java/org/openas2/processor/receiver/PollingModule.java`

- [ ] **Step 1.1: Write the failing test for `TYPE_NOT_FOUND`**

Add to any existing test class (e.g., `TriggerPollCommandTest.java`) or create a small one-off:

```java
@Test
public void commandResultHasNotFoundType() {
    assertEquals("NOT_FOUND", CommandResult.TYPE_NOT_FOUND);
}
```

- [ ] **Step 1.2: Run the test — confirm it fails to compile**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#commandResultHasNotFoundType -pl Server
```

Expected: compilation error — `TYPE_NOT_FOUND` does not exist.

- [ ] **Step 1.3: Add `TYPE_NOT_FOUND` to `CommandResult.java`**

In `Server/src/main/java/org/openas2/cmd/CommandResult.java`, add after `TYPE_SENT`:

```java
public static final String TYPE_NOT_FOUND = "NOT_FOUND";
```

- [ ] **Step 1.4: Promote `isBusy()` and `setBusy()` to `protected` in `PollingModule.java`**

In `Server/src/main/java/org/openas2/processor/receiver/PollingModule.java`, change lines 93–98 from `private` to `protected`:

```java
protected boolean isBusy() {
    return busy;
}

protected void setBusy(boolean b) {
    busy = b;
}
```

- [ ] **Step 1.5: Run the test — confirm it passes**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#commandResultHasNotFoundType -pl Server
```

Expected: PASS. Run the full suite to confirm no regressions:

```bash
./mvnw test -pl Server
```

- [ ] **Step 1.6: Commit**

```bash
git add Server/src/main/java/org/openas2/cmd/CommandResult.java \
        Server/src/main/java/org/openas2/processor/receiver/PollingModule.java \
        Server/src/test/java/org/openas2/app/poll/TriggerPollCommandTest.java
git commit -m "feat: add TYPE_NOT_FOUND constant and expose busy flag as protected"
```

---

## Task 2: Add `triggerFileNow` to `DirectoryPollingModule` (TDD)

`DirectoryPollingModule` is abstract. Tests use `AS2DirectoryPollingModule` (its only concrete subclass, in the same package) with `processSingleFile` overridden to avoid triggering the full AS2 send chain.

**Files:**
- Create: `Server/src/test/java/org/openas2/processor/receiver/DirectoryPollingModuleTest.java`
- Modify: `Server/src/main/java/org/openas2/processor/receiver/DirectoryPollingModule.java`

- [ ] **Step 2.1: Write the first failing test — file not found returns false**

Create `Server/src/test/java/org/openas2/processor/receiver/DirectoryPollingModuleTest.java`:

```java
package org.openas2.processor.receiver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DirectoryPollingModuleTest {

    @TempDir
    Path outboxDir;

    private DirectoryPollingModule poller;
    private boolean processCalled;

    @BeforeEach
    void setUp() {
        processCalled = false;
        // Use AS2DirectoryPollingModule (concrete subclass) with processSingleFile
        // overridden to avoid needing a full AS2 session/processor chain.
        poller = new AS2DirectoryPollingModule() {
            @Override
            protected void processSingleFile(File file, String fileEntryKey) {
                processCalled = true;
            }
        };
        poller.setOutboxDir(outboxDir.toString());
    }

    @Test
    public void triggerFileNow_returnsFalseWhenFileDoesNotExist() throws Exception {
        boolean result = poller.triggerFileNow("nonexistent.edi");
        assertFalse(result);
    }
}
```

- [ ] **Step 2.2: Run the test — confirm it fails to compile**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest#triggerFileNow_returnsFalseWhenFileDoesNotExist -pl Server
```

Expected: compilation error — `triggerFileNow` does not exist.

- [ ] **Step 2.3: Add a stub `triggerFileNow` to `DirectoryPollingModule.java`**

Add at the end of the class, before the closing `}`:

```java
public boolean triggerFileNow(String filename) throws OpenAS2Exception {
    return false;
}
```

- [ ] **Step 2.4: Run the test — confirm it passes**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest#triggerFileNow_returnsFalseWhenFileDoesNotExist -pl Server
```

Expected: PASS.

- [ ] **Step 2.5: Write the second failing test — file found, `processSingleFile` is invoked**

Add to `DirectoryPollingModuleTest`:

```java
@Test
public void triggerFileNow_returnsTrueAndInvokesProcessingWhenFileExists() throws Exception {
    File file = outboxDir.resolve("invoice.edi").toFile();
    file.createNewFile();

    boolean result = poller.triggerFileNow("invoice.edi");

    assertTrue(result);
    assertTrue(processCalled, "processSingleFile should have been called");
}
```

- [ ] **Step 2.6: Run the test — confirm it fails**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest#triggerFileNow_returnsTrueAndInvokesProcessingWhenFileExists -pl Server
```

Expected: FAIL — result is `false` (stub always returns false).

- [ ] **Step 2.7: Implement the full `triggerFileNow` in `DirectoryPollingModule.java`**

Replace the stub with the full implementation. Note that `getCanonicalFile()` throws `IOException`, which must be wrapped:

```java
public boolean triggerFileNow(String filename) throws OpenAS2Exception {
    try {
        File outbox = new File(getOutboxDir()).getCanonicalFile();
        File file = new File(outbox, filename).getCanonicalFile();
        // Layer 2 defense-in-depth: canonical path must remain inside the outbox dir
        if (!file.getCanonicalPath().startsWith(outbox.getCanonicalPath() + File.separator)) {
            throw new OpenAS2Exception("Path traversal detected for filename: " + filename);
        }
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
    } catch (IOException e) {
        throw new OpenAS2Exception("Failed to resolve file path for filename: " + filename, e);
    }
}
```

`lastPollSentFileNames` and `lastPollSentDetails` are `private` fields in `DirectoryPollingModule`. Since `triggerFileNow` is defined in the same class, this is valid.

- [ ] **Step 2.8: Run the two tests — confirm both pass**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest -pl Server
```

Expected: both tests PASS.

- [ ] **Step 2.9: Write the third test — busy poller returns false without calling `processSingleFile`**

Add to `DirectoryPollingModuleTest`:

```java
@Test
public void triggerFileNow_returnsFalseWhenPollerIsBusy() throws Exception {
    File file = outboxDir.resolve("invoice.edi").toFile();
    file.createNewFile();

    poller.setBusy(true);
    boolean result = poller.triggerFileNow("invoice.edi");

    assertFalse(result);
    assertFalse(processCalled, "processSingleFile should NOT have been called when busy");
}
```

- [ ] **Step 2.10: Run — confirm it passes (busy check already in place)**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest#triggerFileNow_returnsFalseWhenPollerIsBusy -pl Server
```

Expected: PASS.

- [ ] **Step 2.11: Write the fourth test — path escaping the outbox via symlink throws**

This test creates a symlink inside the outbox that points to a file outside it. The Layer 2 canonical-path check must detect and reject it. Symlink creation requires elevated privileges on Windows, so restrict to Linux/macOS:

```java
@Test
@EnabledOnOs({OS.LINUX, OS.MAC})
public void triggerFileNow_throwsWhenCanonicalPathEscapesOutboxViaSymlink() throws Exception {
    // Create a real file outside the outbox
    Path externalFile = Files.createTempFile("external", ".edi");
    externalFile.toFile().deleteOnExit();

    // Create a symlink inside the outbox pointing outside
    Path symlink = outboxDir.resolve("link.edi");
    Files.createSymbolicLink(symlink, externalFile);

    // "link.edi" passes Layer 1 (no path separators), but Layer 2 must catch it
    assertThrows(OpenAS2Exception.class, () -> poller.triggerFileNow("link.edi"));
}
```

- [ ] **Step 2.12: Run all `DirectoryPollingModuleTest` tests — confirm all pass**

```bash
./mvnw test -Dtest=DirectoryPollingModuleTest -pl Server
```

Expected: all PASS (symlink test skipped on Windows).

- [ ] **Step 2.13: Run full suite to verify no regressions**

```bash
./mvnw test -pl Server
```

- [ ] **Step 2.14: Commit**

```bash
git add Server/src/main/java/org/openas2/processor/receiver/DirectoryPollingModule.java \
        Server/src/test/java/org/openas2/processor/receiver/DirectoryPollingModuleTest.java
git commit -m "feat: add triggerFileNow to DirectoryPollingModule with canonical path guard"
```

---

## Task 3: Add filename-filter branch to `TriggerPollCommand` (TDD)

The new tests mock `DirectoryPollingModule` directly (Mockito can mock abstract classes).

**Files:**
- Modify: `Server/src/test/java/org/openas2/app/poll/TriggerPollCommandTest.java`
- Modify: `Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java`

- [ ] **Step 3.1: Write the first new failing test — path traversal with `/` is rejected**

Add to `TriggerPollCommandTest`:

```java
@Test
public void executeReturnsErrorWhenFilenameContainsForwardSlash() throws Exception {
    Session session = mock(Session.class);
    when(session.getOutboundPollingModules()).thenReturn(Collections.emptyList());

    TriggerPollCommand command = new TriggerPollCommand();
    command.init(session, new HashMap<>());

    // params[0] = action name (ignored by execute), params[1] = filename
    CommandResult result = command.execute(new Object[]{"trigger", "../../etc/passwd"});

    assertEquals(CommandResult.TYPE_ERROR, result.getType());
    assertTrue(result.getResult().contains("Invalid filename"));
    // Verify no pollers were consulted
    verify(session, never()).getOutboundPollingModules();
}
```

- [ ] **Step 3.2: Run the test — confirm it fails**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsErrorWhenFilenameContainsForwardSlash -pl Server
```

Expected: FAIL — `execute` currently ignores params, so session IS called and result type is `OK`, not `ERROR`.

- [ ] **Step 3.3: Add the filename-filter branch to `TriggerPollCommand.execute()`**

In `Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java`, modify `execute` to branch on whether a filename param was provided. The filename is at `params[1]` when present. Add a private helper for validation:

```java
private static boolean isUnsafeFilename(String filename) {
    return filename.contains("/") || filename.contains("\\") || filename.contains("..");
}
```

At the top of `execute`, before the existing `getSession()` call, insert:

```java
// Filename-filter mode: params[1] carries the target filename when present
if (params != null && params.length >= 2) {
    String filename = params[1].toString().trim();
    if (!filename.isEmpty()) {
        return executeForFile(filename);
    }
}
```

Add the `executeForFile` method to the class:

```java
private CommandResult executeForFile(String filename) {
    // Layer 1 — reject filenames with any path component
    if (isUnsafeFilename(filename)) {
        return new CommandResult(CommandResult.TYPE_ERROR,
                "Invalid filename: path components are not allowed.");
    }

    Session session = getSession();
    List<PollingModule> pollers;
    try {
        pollers = session.getOutboundPollingModules();
    } catch (OpenAS2Exception e) {
        LOGGER.error("Failed to obtain outbound polling modules", e);
        return new CommandResult(CommandResult.TYPE_ERROR, "Poll failed: " + e.getMessage());
    }

    LOGGER.info("Triggering targeted file send for '{}' across {} poller(s)", filename, pollers.size());

    List<String> allFiles = new ArrayList<>();
    List<String> allSentLines = new ArrayList<>();
    List<Map<String, Object>> sentByOutbox = new ArrayList<>();
    List<String> outboxesChecked = new ArrayList<>();
    boolean anyFound = false;

    for (PollingModule poller : pollers) {
        if (!(poller instanceof DirectoryPollingModule)) {
            continue; // Only directory pollers have an outbox to check
        }
        DirectoryPollingModule dpm = (DirectoryPollingModule) poller;
        String outboxId = getOutboxIdentifier(poller);
        try {
            boolean found = dpm.triggerFileNow(filename);
            if (found) {
                anyFound = true;
                outboxesChecked.add(outboxId);
                List<String> sentNames = dpm.getLastPollSentFileNames();
                if (!sentNames.isEmpty()) {
                    allFiles.addAll(sentNames);
                    allSentLines.add("Outbox " + outboxId + ": sent " + String.join(", ", sentNames));

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("outbox", outboxId);

                    List<Map<String, String>> details = dpm.getLastPollSentDetails();
                    if (!details.isEmpty()) {
                        Map<String, String> first = details.get(0);
                        if (first.containsKey("sender")) entry.put("sender", first.get("sender"));
                        if (first.containsKey("receiver")) entry.put("receiver", first.get("receiver"));
                    }
                    entry.put("files", new ArrayList<>(sentNames));
                    sentByOutbox.add(entry);
                }
            }
        } catch (OpenAS2Exception e) {
            LOGGER.error("Targeted file send failed for poller {}", poller.getOutboxDir(), e);
            return new CommandResult(CommandResult.TYPE_ERROR, "Poll failed: " + e.getMessage());
        }
    }

    if (!anyFound) {
        return new CommandResult(CommandResult.TYPE_NOT_FOUND,
                "File '" + filename + "' not found in any outbox.");
    }

    CommandResult result = allSentLines.isEmpty()
            ? new CommandResult(CommandResult.TYPE_OK)
            : new CommandResult(CommandResult.TYPE_SENT);

    result.getResults().add("Poll completed for " + outboxesChecked.size() + " poller(s).");
    result.getResults().addAll(allSentLines);

    Map<String, Object> pollData = new LinkedHashMap<>();
    pollData.put("outboxesChecked", outboxesChecked);
    pollData.put("allFiles", allFiles);
    pollData.put("sentByOutbox", sentByOutbox);
    Map<String, Object> pollWrapper = new HashMap<>();
    pollWrapper.put("poll", pollData);
    result.getResults().add(pollWrapper);

    return result;
}
```

- [ ] **Step 3.4: Run the first new test — confirm it passes**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsErrorWhenFilenameContainsForwardSlash -pl Server
```

Expected: PASS.

- [ ] **Step 3.5: Write remaining Layer 1 rejection tests**

Add to `TriggerPollCommandTest`:

```java
@Test
public void executeReturnsErrorWhenFilenameContainsBackslash() throws Exception {
    TriggerPollCommand command = new TriggerPollCommand();
    command.init(mock(Session.class), new HashMap<>());

    CommandResult result = command.execute(new Object[]{"trigger", "file\\etc.edi"});

    assertEquals(CommandResult.TYPE_ERROR, result.getType());
    assertTrue(result.getResult().contains("Invalid filename"));
}

@Test
public void executeReturnsErrorWhenFilenameContainsDotDot() throws Exception {
    TriggerPollCommand command = new TriggerPollCommand();
    command.init(mock(Session.class), new HashMap<>());

    CommandResult result = command.execute(new Object[]{"trigger", "..\\secret.edi"});

    assertEquals(CommandResult.TYPE_ERROR, result.getType());
    assertTrue(result.getResult().contains("Invalid filename"));
}
```

- [ ] **Step 3.6: Run them — confirm they pass**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsErrorWhenFilenameContainsBackslash+executeReturnsErrorWhenFilenameContainsDotDot -pl Server
```

Expected: both PASS.

- [ ] **Step 3.7: Write the NOT_FOUND test — valid filename, no poller finds the file**

```java
@Test
public void executeReturnsNotFoundWhenNoPollerFindsTheFile() throws Exception {
    Session session = mock(Session.class);
    DirectoryPollingModule poller = mock(DirectoryPollingModule.class);
    when(poller.getOutboxDir()).thenReturn("/path/to/outbox");
    when(poller.triggerFileNow("missing.edi")).thenReturn(false);
    when(session.getOutboundPollingModules()).thenReturn(Collections.singletonList(poller));

    TriggerPollCommand command = new TriggerPollCommand();
    command.init(session, new HashMap<>());

    CommandResult result = command.execute(new Object[]{"trigger", "missing.edi"});

    assertEquals(CommandResult.TYPE_NOT_FOUND, result.getType());
    assertTrue(result.getResult().contains("missing.edi"));
}
```

- [ ] **Step 3.8: Run — confirm it passes**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsNotFoundWhenNoPollerFindsTheFile -pl Server
```

Expected: PASS.

- [ ] **Step 3.9: Write the SENT test — one poller finds and sends the file**

```java
@Test
@SuppressWarnings("unchecked")
public void executeReturnsSentWhenPollerFindsAndSendsFile() throws Exception {
    Session session = mock(Session.class);
    DirectoryPollingModule poller = mock(DirectoryPollingModule.class);
    when(poller.getOutboxDir()).thenReturn("/path/to/outbox");
    when(poller.triggerFileNow("invoice.edi")).thenReturn(true);
    when(poller.getLastPollSentFileNames()).thenReturn(Arrays.asList("invoice.edi"));
    when(poller.getLastPollSentDetails()).thenReturn(Collections.emptyList());
    when(session.getOutboundPollingModules()).thenReturn(Collections.singletonList(poller));

    TriggerPollCommand command = new TriggerPollCommand();
    command.init(session, new HashMap<>());

    CommandResult result = command.execute(new Object[]{"trigger", "invoice.edi"});

    assertEquals(CommandResult.TYPE_SENT, result.getType());
    assertTrue(result.getResult().contains("invoice.edi"));

    // Verify poll data structure
    Object lastEntry = result.getResults().get(result.getResults().size() - 1);
    assertTrue(lastEntry instanceof Map);
    Map<String, Object> pollWrapper = (Map<String, Object>) lastEntry;
    Map<String, Object> poll = (Map<String, Object>) pollWrapper.get("poll");
    List<String> allFiles = (List<String>) poll.get("allFiles");
    assertTrue(allFiles.contains("invoice.edi"));
}
```

- [ ] **Step 3.10: Run — confirm it passes**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsSentWhenPollerFindsAndSendsFile -pl Server
```

Expected: PASS.

- [ ] **Step 3.11: Write the skip test — non-DirectoryPollingModule pollers are ignored**

```java
@Test
public void executeReturnsNotFoundWhenOnlyNonDirectoryPollersExist() throws Exception {
    Session session = mock(Session.class);
    PollingModule plainPoller = mock(PollingModule.class);
    when(session.getOutboundPollingModules()).thenReturn(Collections.singletonList(plainPoller));

    TriggerPollCommand command = new TriggerPollCommand();
    command.init(session, new HashMap<>());

    CommandResult result = command.execute(new Object[]{"trigger", "invoice.edi"});

    assertEquals(CommandResult.TYPE_NOT_FOUND, result.getType());
    // Plain PollingModule should never have triggerFileNow called (it doesn't have the method)
    verifyNoInteractions(plainPoller);
}
```

- [ ] **Step 3.12: Run — confirm it passes**

```bash
./mvnw test -Dtest=TriggerPollCommandTest#executeReturnsNotFoundWhenOnlyNonDirectoryPollersExist -pl Server
```

Expected: PASS.

- [ ] **Step 3.13: Confirm all existing `TriggerPollCommandTest` tests still pass (no regressions)**

```bash
./mvnw test -Dtest=TriggerPollCommandTest -pl Server
```

Expected: all PASS, including the original 4 tests.

- [ ] **Step 3.14: Run full suite**

```bash
./mvnw test -pl Server
```

- [ ] **Step 3.15: Commit**

```bash
git add Server/src/main/java/org/openas2/app/poll/TriggerPollCommand.java \
        Server/src/test/java/org/openas2/app/poll/TriggerPollCommandTest.java
git commit -m "feat: add filename-filter branch to TriggerPollCommand with Layer 1 path validation"
```

---

## Task 4: Add `triggerPollWithBody` to `ApiResource` (TDD)

`ApiResource` is JAX-RS wiring code. The unit logic is already tested in Tasks 2–3. Test here at the HTTP integration level using the existing `RestApiTest` pattern (real HTTP client against a running server instance).

**Files:**
- Modify: `Server/src/test/java/org/openas2/app/RestApiTest.java`
- Modify: `Server/src/main/java/org/openas2/cmd/processor/restapi/ApiResource.java`

- [ ] **Step 4.1: Write the failing integration test — JSON body with unknown filename returns NOT_FOUND**

Add to `RestApiTest.java`. Place it after the existing test methods, following the `test_X_` naming convention the file uses:

```java
@Test
public void test_Z_pollTriggerWithJsonBodyReturnsNotFoundForMissingFile() throws Exception {
    HttpPost post = new HttpPost(baseUrl + "poll/trigger");
    post.setHeader("Content-Type", "application/json");
    // Use a StringEntity to send raw JSON
    post.setEntity(new org.apache.http.entity.StringEntity(
            "{\"file\":\"nonexistent-file-xyz.edi\"}", "UTF-8"));

    try (CloseableHttpResponse response = httpclient.execute(post)) {
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        String body = EntityUtils.toString(response.getEntity());
        assertThat(body, containsString("NOT_FOUND"));
        assertThat(body, containsString("nonexistent-file-xyz.edi"));
    }
}
```

The test uses `baseUrl = "http://127.0.0.1:8080/api/"`, so the URL becomes `http://127.0.0.1:8080/api/poll/trigger`. The `httpclient` is already configured with basic auth in `@BeforeAll`.

- [ ] **Step 4.2: Run the integration test — confirm it fails**

```bash
./mvnw test -Dtest=RestApiTest#test_Z_pollTriggerWithJsonBodyReturnsNotFoundForMissingFile -pl Server
```

Expected: FAIL — the JSON endpoint doesn't exist yet; the existing form-encoded handler will reject the request with `415 Unsupported Media Type`, or the response body will not contain `NOT_FOUND`.

- [ ] **Step 4.3: Add `triggerPollWithBody` to `ApiResource.java`**

Add the following imports if not already present:
```java
import java.util.Map;
import jakarta.ws.rs.Consumes;
```
(`Consumes` is already imported; `Map` is already imported. Verify before adding.)

Add the new method to `ApiResource`. Place it before `postCommand` for clarity:

```java
@RolesAllowed({"ADMIN"})
@POST
@Path("poll/trigger")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public Response triggerPollWithBody(Map<String, String> body) throws Exception {
    try {
        List<String> params = new ArrayList<>();
        params.add("trigger");
        if (body != null && body.containsKey("file")) {
            params.add(body.get("file"));
        }
        CommandResult output = getProcessor().feedCommand("poll", params);
        String jsonResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
        LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
        throw ex;
    }
}
```

**Routing note:** Jersey selects this method for `POST /api/poll/trigger` requests because literal paths beat parameterized ones. The existing `postCommand` (with `@Consumes(APPLICATION_FORM_URLENCODED)`) continues to handle form-encoded requests.

- [ ] **Step 4.4: Run the integration test — confirm it passes**

```bash
./mvnw test -Dtest=RestApiTest#test_Z_pollTriggerWithJsonBodyReturnsNotFoundForMissingFile -pl Server
```

Expected: PASS.

- [ ] **Step 4.5: Run the full test suite — confirm no regressions**

```bash
./mvnw test -pl Server
```

Expected: all PASS. Pay particular attention to other `RestApiTest` tests to confirm the existing form-encoded `poll/trigger` still works.

- [ ] **Step 4.6: Commit**

```bash
git add Server/src/main/java/org/openas2/cmd/processor/restapi/ApiResource.java \
        Server/src/test/java/org/openas2/app/RestApiTest.java
git commit -m "feat: add JSON-body poll trigger endpoint to ApiResource"
```

---

## Verification Checklist

Before marking the feature complete:

- [ ] All new methods have tests written before implementation
- [ ] Each test was confirmed to fail before implementation
- [ ] Each test was confirmed to pass after implementation
- [ ] `./mvnw test -pl Server` is fully green
- [ ] Output has no unexpected warnings or errors
- [ ] Layer 1 rejection tested: `/`, `\`, `..` in filename
- [ ] Layer 2 canonical path check tested (Linux/macOS only)
- [ ] Existing `TriggerPollCommandTest` tests unchanged and passing
- [ ] Existing `RestApiTest` tests unchanged and passing

---

## Manual Smoke Test

After all tasks complete, verify end-to-end with curl against a running server:

```bash
# Should return NOT_FOUND (file doesn't exist in any outbox)
curl -s -X POST -u "userID:pWd" \
  -H "Content-Type: application/json" \
  -d '{"file":"invoice.edi"}' \
  "http://localhost:8080/api/poll/trigger" | python -m json.tool

# Should return ERROR (path traversal rejected)
curl -s -X POST -u "userID:pWd" \
  -H "Content-Type: application/json" \
  -d '{"file":"../../secret.txt"}' \
  "http://localhost:8080/api/poll/trigger" | python -m json.tool

# Should return OK (existing full-poll behavior unchanged)
curl -s -X POST -u "userID:pWd" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  "http://localhost:8080/api/poll/trigger" | python -m json.tool
```

Powershell:
```ps
curl.exe --% -s -i -X POST -u "userID:pWd" -H "Content-Type: application/json" -d "{\"file\":\"fiile.txt\"}" "http://localhost:8080/api/poll/trigger"
```