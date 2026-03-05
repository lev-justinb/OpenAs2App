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
        List<PollingModule> pollers = Arrays.asList(poller1, poller2);
        when(session.getOutboundPollingModules()).thenReturn(pollers);

        TriggerPollCommand command = new TriggerPollCommand();
        command.init(session, new HashMap<String, String>());

        CommandResult result = command.execute(new Object[0]);

        assertEquals(CommandResult.TYPE_OK, result.getType());
        String message = result.getResult();
        assertTrue(message.contains("Poll completed for 2 poller(s)."));
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
    }
}
