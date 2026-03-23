package org.openas2.app.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.CommandResult;
import org.openas2.processor.receiver.PollingModule;

public class TriggerPollCommandTest {

    @Test
    public void executeReturnsOkWhenPollersTriggerSuccessfully() throws Exception {
        Session session = mock(Session.class);
        PollingModule poller1 = mock(PollingModule.class);
        PollingModule poller2 = mock(PollingModule.class);
        when(poller1.getOutboxDir()).thenReturn("/path/to/outbox1");
        when(poller2.getOutboxDir()).thenReturn("/path/to/outbox2");
        List<PollingModule> pollers = Arrays.asList(poller1, poller2);
        when(session.getOutboundPollingModules()).thenReturn(pollers);

        TriggerPollCommand command = new TriggerPollCommand();
        command.init(session, new HashMap<String, String>());

        CommandResult result = command.execute(new Object[0]);

        assertEquals(CommandResult.TYPE_OK, result.getType());
        String message = result.getResult();
        assertTrue(message.contains("Poll completed for 2 poller(s)."));

        List<Object> results = result.getResults();
        assertTrue(results.size() >= 3);
        Object lastResult = results.get(results.size() - 1);
        assertTrue(lastResult instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> pollWrapper = (Map<String, Object>) lastResult;
        assertTrue(pollWrapper.containsKey("poll"));
        @SuppressWarnings("unchecked")
        Map<String, Object> poll = (Map<String, Object>) pollWrapper.get("poll");
        assertTrue(poll.containsKey("outboxesChecked"));
        assertTrue(poll.containsKey("allFiles"));
        assertTrue(poll.containsKey("sentByOutbox"));
        assertEquals(2, ((List<?>) poll.get("outboxesChecked")).size());
        assertTrue(((List<?>) poll.get("allFiles")).isEmpty());
        assertTrue(((List<?>) poll.get("sentByOutbox")).isEmpty());
    }

    @Test
    public void executeReturnsErrorWhenGettingPollersFails() throws Exception {
        Session session = mock(Session.class);
        when(session.getOutboundPollingModules()).thenThrow(new OpenAS2Exception("test failure"));

        TriggerPollCommand command = new TriggerPollCommand();
        command.init(session, new HashMap<String, String>());

        CommandResult result = command.execute(new Object[0]);

        assertEquals(CommandResult.TYPE_ERROR, result.getType());
        String message = result.getResult();
        assertTrue(message.contains("Poll failed: test failure"));
    }

    @Test
    public void executeReturnsErrorWhenPollerThrows() throws Exception {
        Session session = mock(Session.class);
        PollingModule poller = mock(PollingModule.class);
        when(session.getOutboundPollingModules()).thenReturn(Collections.singletonList(poller));
        doThrow(new OpenAS2Exception("poll error")).when(poller).triggerPollNow();

        TriggerPollCommand command = new TriggerPollCommand();
        command.init(session, new HashMap<String, String>());

        CommandResult result = command.execute(new Object[0]);

        assertEquals(CommandResult.TYPE_ERROR, result.getType());
        String message = result.getResult();
        assertTrue(message.contains("Poll failed: poll error"));
    }

    @Test
    public void commandResultHasNotFoundType() {
        assertEquals("NOT_FOUND", CommandResult.TYPE_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void executeIncludesSentFileNamesInResult() throws Exception {
        Session session = mock(Session.class);
        PollingModule poller = mock(PollingModule.class);
        when(session.getOutboundPollingModules()).thenReturn(Collections.singletonList(poller));
        when(poller.getOutboxDir()).thenReturn("/path/to/outbox");
        when(poller.getLastPollSentFileNames()).thenReturn(Arrays.asList("file1.edi", "file2.edi"));

        TriggerPollCommand command = new TriggerPollCommand();
        command.init(session, new HashMap<String, String>());

        CommandResult result = command.execute(new Object[0]);

        assertEquals(CommandResult.TYPE_SENT, result.getType());
        List<Object> results = result.getResults();
        assertTrue(results.toString().contains("Poll completed for 1 poller(s)."));
        assertTrue(results.toString().contains("file1.edi"));
        assertTrue(results.toString().contains("file2.edi"));
        assertTrue(results.toString().contains("Outbox outbox"));
        assertTrue(results.toString().contains("sent"));

        Object lastResult = results.get(results.size() - 1);
        assertTrue(lastResult instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> pollWrapper = (Map<String, Object>) lastResult;
        @SuppressWarnings("unchecked")
        Map<String, Object> poll = (Map<String, Object>) pollWrapper.get("poll");
        List<String> outboxesChecked = (List<String>) poll.get("outboxesChecked");
        assertEquals(1, outboxesChecked.size());
        assertEquals("outbox", outboxesChecked.get(0));
        List<String> allFiles = (List<String>) poll.get("allFiles");
        assertEquals(2, allFiles.size());
        assertTrue(allFiles.contains("file1.edi"));
        assertTrue(allFiles.contains("file2.edi"));
        List<Map<String, Object>> sentByOutbox = (List<Map<String, Object>>) poll.get("sentByOutbox");
        assertEquals(1, sentByOutbox.size());
        assertEquals("outbox", sentByOutbox.get(0).get("outbox"));
        assertEquals(Arrays.asList("file1.edi", "file2.edi"), sentByOutbox.get(0).get("files"));
    }
}
