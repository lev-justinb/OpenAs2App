package org.openas2.app.poll;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.processor.receiver.PollingModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriggerPollCommand extends BaseCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerPollCommand.class);

    public String getDefaultName() {
        return "trigger";
    }

    public String getDefaultDescription() {
        return "Trigger an immediate poll for all outbound polling modules and reset their timers.";
    }

    public String getDefaultUsage() {
        return "trigger";
    }

    @Override
    public CommandResult execute(Object[] params) {
        Session session = getSession();
        List<PollingModule> pollers;
        try {
            pollers = session.getOutboundPollingModules();
        } catch (OpenAS2Exception e) {
            LOGGER.error("Failed to obtain outbound polling modules", e);
            return new CommandResult(CommandResult.TYPE_ERROR, "Poll failed: " + e.getMessage());
        }

        LOGGER.info("Triggering on-demand poll for {} outbound polling module(s)", pollers.size());

        int triggeredCount = 0;
        List<String> allSentLines = new ArrayList<>();
        for (PollingModule poller : pollers) {
            try {
                poller.triggerPollNow();
                triggeredCount++;
                List<String> sentNames = poller.getLastPollSentFileNames();
                if (!sentNames.isEmpty()) {
                    String path = poller.getOutboxDir();
                    if (path != null) {
                        Path p = Paths.get(path);
                        path = p.getFileName().toString();
                    } else {
                        path = poller.getName();
                    }

                    String fileList = String.join(", ", sentNames);
                    allSentLines.add("Outbox " + path + ": sent " + fileList);
                }
            } catch (OpenAS2Exception e) {
                LOGGER.error("On-demand poll failed for poller {}", poller.getOutboxDir(), e);
                return new CommandResult(CommandResult.TYPE_ERROR, "Poll failed: " + e.getMessage());
            }
        }
        CommandResult result;
        if (allSentLines.isEmpty()) {
            result = new CommandResult(CommandResult.TYPE_OK);
        } else {
            result = new CommandResult(CommandResult.TYPE_SENT);
        }

        result.getResults().add("Poll completed for " + triggeredCount + " poller(s).");
        if (allSentLines.isEmpty()) {
            result.getResults().add("No files sent.");
        } else {
            result.getResults().addAll(allSentLines);
        }
        LOGGER.info("On-demand poll completed for {} polling module(s)", triggeredCount);
        return result;
    }
}
