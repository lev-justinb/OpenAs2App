package org.openas2.processor.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.partner.Partnership;
import org.openas2.processor.storage.StorageModule;

public class ToolLauncherModuleTest {

    private ToolLauncherModule module;
    private Session session;
    private AS2Message message;
    private Map<String, Object> options;

    @BeforeEach
    public void setUp() throws Exception {
        module = new ToolLauncherModule();
        session = mock(Session.class);
        message = mock(AS2Message.class);
        options = new HashMap<>();

        when(message.getProtocol()).thenReturn("as2");
    }

    @Test
    public void getModuleActionReturnsDoStore() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("enabled", "true");
        opts.put("command", "echo");
        opts.put("module_action", "store");
        opts.put("filename", "/tmp/test-$rand.12345$.edi");
        module.init(session, opts);

        assertEquals(StorageModule.DO_STORE, module.getModuleAction());
    }

    @Test
    public void canHandleReturnsFalseWhenDisabled() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("enabled", "false");
        opts.put("command", "echo");
        opts.put("module_action", "store");
        opts.put("filename", "/tmp/test.edi");
        module.init(session, opts);

        assertFalse(module.canHandle(StorageModule.DO_STORE, message, options));
    }

    @Test
    public void canHandleReturnsFalseWhenCommandEmpty() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("enabled", "true");
        opts.put("command", "");
        opts.put("module_action", "store");
        opts.put("filename", "/tmp/test.edi");
        module.init(session, opts);

        assertFalse(module.canHandle(StorageModule.DO_STORE, message, options));
    }

    @Test
    public void canHandleReturnsTrueWhenEnabledAndConfigured() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("enabled", "true");
        opts.put("command", "echo");
        opts.put("module_action", "store");
        opts.put("filename", "/tmp/test.edi");
        opts.put("protocol", "as2");
        module.init(session, opts);

        assertTrue(module.canHandle(StorageModule.DO_STORE, message, options));
    }

    @Test
    public void handleDoesNotThrowWhenFileNotFound() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("enabled", "true");
        opts.put("command", "echo");
        opts.put("module_action", "store");
        opts.put("filename", "/nonexistent/path-$rand.12345$.edi");
        opts.put("protocol", "as2");
        module.init(session, opts);

        Partnership partnership = mock(Partnership.class);
        when(message.getPartnership()).thenReturn(partnership);
        when(message.getLogMsgID()).thenReturn(" [test-msg-id]");

        options.put(Partnership.PA_STORE_RECEIVED_FILE_TO, "/nonexistent/path-$rand.12345$.edi");

        module.handle(StorageModule.DO_STORE, message, options);
    }
}
