package org.openas2.processor.receiver;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.params.InvalidParameterException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public abstract class PollingModule extends MessageBuilderModule {
    protected final String PARAM_POLLING_INTERVAL = "interval";
    private Timer timer;
    private boolean busy;
    private String outboxDir;

    public String getOutboxDir() {
        return outboxDir;
    }

    public void setOutboxDir(String outboxDir) {
        this.outboxDir = outboxDir;
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        getParameter(PARAM_POLLING_INTERVAL, true);
    }

    private int getInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_POLLING_INTERVAL, true);
    }

    public abstract void poll();

    public void doStart() throws OpenAS2Exception {
        timer = new Timer(getName(), false);
        timer.scheduleAtFixedRate(new PollTask(), 0, getInterval() * 1000);
    }

    public void doStop() throws OpenAS2Exception {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public synchronized void triggerPollNow() throws OpenAS2Exception {
        try {
            if (!isBusy()) {
                setBusy(true);
                try {
                    poll();
                    poll();
                } finally {
                    setBusy(false);
                }
            }

            int intervalSeconds = getInterval();

            if (timer != null) {
                timer.cancel();
            }

            timer = new Timer(getName(), false);
            long intervalMillis = intervalSeconds * 1000L;
            timer.scheduleAtFixedRate(new PollTask(), intervalMillis, intervalMillis);
        } catch (InvalidParameterException e) {
            throw new OpenAS2Exception("Failed to trigger poll due to invalid interval configuration", e);
        }
    }

    public List<String> getLastPollSentFileNames() {
        return Collections.emptyList();
    }

    private boolean isBusy() {
        return busy;
    }

    private void setBusy(boolean b) {
        busy = b;
    }

    private class PollTask extends TimerTask {
        public void run() {
            if (!isBusy()) {
                setBusy(true);
                poll();
                setBusy(false);
            } else {
                System.out.println("Miss tick: " + getOutboxDir());
            }
        }
    }

}
