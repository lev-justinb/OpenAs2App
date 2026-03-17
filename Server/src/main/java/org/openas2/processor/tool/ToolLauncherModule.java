package org.openas2.processor.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.BaseProcessorModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.IOUtil;

import java.io.File;
import java.util.Map;

/**
 * Processor module that launches an external CLI/EXE when an AS2 file is successfully
 * received and stored. Disabled by default. Uses JDK ProcessBuilder (no new dependencies).
 * Tool execution is async (fire-and-forget); failures are logged but do not affect receive or MDN.
 */
public class ToolLauncherModule extends BaseProcessorModule {

    public static final String PARAM_COMMAND = "command";
    public static final String PARAM_PROTOCOL = "protocol";
    public static final String PARAM_FILENAME = "filename";

    private static final Logger LOG = LoggerFactory.getLogger(ToolLauncherModule.class);

    private boolean enabled;
    private String command;
    private String filenameTemplate;

    @Override
    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        try {
            enabled = "true".equalsIgnoreCase(getParameter("enabled", "false"));
            command = getParameter(PARAM_COMMAND, "");
            filenameTemplate = getParameter(PARAM_FILENAME, false);
        } catch (InvalidParameterException e) {
            throw new OpenAS2Exception(e);
        }
        if (enabled && (command == null || command.trim().isEmpty())) {
            LOG.warn("ToolLauncherModule is enabled but command is empty; tool will not be invoked");
        }
    }

    @Override
    public String getModuleAction() {
        return StorageModule.DO_STORE;
    }

    @Override
    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        try {
            if (!super.canHandle(action, msg, options)) {
                return false;
            }
            if (!enabled || command == null || command.trim().isEmpty()) {
                return false;
            }
            String modProtocol = getParameter(PARAM_PROTOCOL, false);
            if (modProtocol != null && msg.getProtocol() != null && !modProtocol.equals(msg.getProtocol())) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        try {
            String storeTemplate = (String) options.get(Partnership.PA_STORE_RECEIVED_FILE_TO);
            if (storeTemplate == null) {
                storeTemplate = filenameTemplate;
            }
            if (storeTemplate == null || storeTemplate.trim().isEmpty()) {
                LOG.warn("ToolLauncherModule: no store template available, cannot resolve file path" + msg.getLogMsgID());
                return;
            }

            CompositeParameters parser = new CompositeParameters(false)
                    .add("date", new DateParameters())
                    .add("msg", new MessageParameters(msg))
                    .add("rand", new RandomParameters());
            String resolvedPath = ParameterParser.parse(storeTemplate, parser);
            resolvedPath = IOUtil.cleanFilename(resolvedPath);

            File file = new File(resolvedPath);
            if (!file.exists()) {
                LOG.warn("ToolLauncherModule: stored file not found at " + resolvedPath + msg.getLogMsgID());
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(command.trim(), file.getAbsolutePath());
            pb.inheritIO();
            
            Process process = pb.start();
            if (LOG.isInfoEnabled()) {
                LOG.info("ToolLauncherModule launched " + command + " for " + file.getAbsolutePath() + msg.getLogMsgID());
            }
        } catch (Exception e) {
            LOG.error("ToolLauncherModule failed to launch tool for message " + msg.getLogMsgID() + ": " + e.getMessage(), e);
        }
    }
}
