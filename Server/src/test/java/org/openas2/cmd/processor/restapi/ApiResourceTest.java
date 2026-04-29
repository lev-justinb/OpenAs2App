package org.openas2.cmd.processor.restapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openas2.cmd.CommandResult;

public class ApiResourceTest {

    @Test
    public void pollTriggerHttpStatusUsesBadGatewayForCommandError() {
        CommandResult output = new CommandResult(CommandResult.TYPE_ERROR);
        assertEquals(502, ApiResource.getPollTriggerHttpStatus(output));
    }

    @Test
    public void pollTriggerHttpStatusUsesNotFoundForCommandNotFound() {
        CommandResult output = new CommandResult(CommandResult.TYPE_NOT_FOUND);
        assertEquals(404, ApiResource.getPollTriggerHttpStatus(output));
    }

    @Test
    public void pollTriggerHttpStatusUsesOkForSuccessfulResults() {
        assertEquals(200, ApiResource.getPollTriggerHttpStatus(new CommandResult(CommandResult.TYPE_OK)));
        assertEquals(200, ApiResource.getPollTriggerHttpStatus(new CommandResult(CommandResult.TYPE_SENT)));
    }
}
