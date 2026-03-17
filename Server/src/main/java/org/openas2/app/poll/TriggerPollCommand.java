package org.openas2.app.poll;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.processor.receiver.DirectoryPollingModule;
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
        List<String> outboxesChecked = new ArrayList<>();
        List<String> allFiles = new ArrayList<>();
        List<Map<String, Object>> sentByOutbox = new ArrayList<>();

        for (PollingModule poller : pollers) {
            try {
                String outboxId = getOutboxIdentifier(poller);
                poller.triggerPollNow();
                triggeredCount++;
                outboxesChecked.add(outboxId);

                List<String> sentNames = poller.getLastPollSentFileNames();
                if (!sentNames.isEmpty()) {
                    allFiles.addAll(sentNames);

                    String sender = null;
                    String receiver = null;
                    if (poller instanceof DirectoryPollingModule) {
                        List<Map<String, String>> details = ((DirectoryPollingModule) poller).getLastPollSentDetails();
                        if (!details.isEmpty()) {
                            Map<String, String> first = details.get(0);
                            sender = first.get("sender");
                            receiver = first.get("receiver");
                        }
                    }

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("outbox", outboxId);
                    if (sender != null) {
                        entry.put("sender", sender);
                    }
                    if (receiver != null) {
                        entry.put("receiver", receiver);
                    }
                    entry.put("files", new ArrayList<>(sentNames));
                    sentByOutbox.add(entry);

                    String fileList = String.join(", ", sentNames);
                    allSentLines.add("Outbox " + outboxId + ": sent " + fileList);
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

        Map<String, Object> pollData = new LinkedHashMap<>();
        pollData.put("outboxesChecked", outboxesChecked);
        pollData.put("allFiles", allFiles);
        pollData.put("sentByOutbox", sentByOutbox);
        Map<String, Object> pollWrapper = new HashMap<>();
        pollWrapper.put("poll", pollData);
        result.getResults().add(pollWrapper);

        LOGGER.info("On-demand poll completed for {} polling module(s)", triggeredCount);
        return result;
    }

    private static String getOutboxIdentifier(PollingModule poller) {
        String path = poller.getOutboxDir();
        if (path != null) {
            return Paths.get(path).getFileName().toString();
        }
        return poller.getName();
    }
}
