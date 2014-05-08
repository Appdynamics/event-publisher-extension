package com.appdynamics.monitors.events;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.thoughtworks.xstream.XStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

public class EventPublisherMonitor extends AManagedMonitor {

    private static final Logger LOG = Logger.getLogger(EventPublisherMonitor.class);
    private static final int WORKER_COUNT = 5; // Max worker count can be altered via configuration.
    private static final String EVENT_PUBLISHING_URL = "http://localhost:8293/machineagent/event";
    private static final String QUERY_STRING_TYPE = "type";
    private static final String QUERY_STRING_SUMMARY = "summary";
    private static final String EVENT_TYPE_INFO = "info";

    private boolean isScheduled = false;

    private Integer workerCount = WORKER_COUNT;

    private Collection<EventScript> eventScripts; // Collection of tasks (implementing java.lang.Runnable)

    public EventPublisherMonitor() {
        String details = EventPublisherMonitor.class.getPackage().getImplementationTitle();
        String msg = "Using Monitor Version [" + details + "]";
        LOG.info(msg);
        System.out.println(msg);
    }

    private void parseAndPopulate(String instrumentXMLPath) {

        XStream xstream = new XStream();
        xstream.alias("events", List.class);
        xstream.alias("event", EventScript.class);
        xstream.alias("output", EventScript.Output.class);

        xstream.autodetectAnnotations(true);
        eventScripts = (List<EventScript>) xstream.fromXML(new File(instrumentXMLPath));
    }

    /**
     * @see com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map, com.singularity.ee.agent.systemagent.api.TaskExecutionContext)
     */
    public TaskOutput execute(final Map<String, String> args, final TaskExecutionContext context)
            throws TaskExecutionException {

        if(isScheduled) {
            return new TaskOutput("Event Publishing Extension Monitor already scheduled");
        }

        String projectFolder = args.get("project_path");

        final String strWorkerCount = args.get("worker_count");

        if (strWorkerCount != null && !"".equals(strWorkerCount.trim())) {
            try {
                this.workerCount = Integer.parseInt(strWorkerCount);
            } catch (NumberFormatException e) {
                // Ignore.
            }
        }

        // Parse the scripts.xml.
        parseAndPopulate(projectFolder + "/events.xml");

        final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(this.workerCount);

        // Add the tasks to execute them in some time in future.
        for (final EventScript script : eventScripts) {

            //If max wait time is not defined or is more than the period specified then reset it to period specified.
            if(script.getMaxWaitTime() == 0 || script.getMaxWaitTime() > script.getPeriod()) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Wait time for the script "+script.getName()+" is more than period. Resetting it to the value specified in the period");
                }
                script.setMaxWaitTime(script.getPeriod());
            }

            //Submit the periodic task according to period specified.
            scheduledThreadPoolExecutor.scheduleAtFixedRate(script, 0, script.getPeriod(), TimeUnit.SECONDS);
        }

        // Send the result across the controller.
        final ScheduledThreadPoolExecutor outputScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
        outputScheduledThreadPoolExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                publishEventOnStatusChange();
            }

        }, 0, 60, TimeUnit.SECONDS);

        isScheduled = true;

        return new TaskOutput("Finished executing Event Publishing Extension Monitor");

    }

    private void publishEventOnStatusChange() {
        HttpClient httpClient = new HttpClient();
        for (EventScript eventScript : eventScripts) {
            if (eventScript.isStatusChanged()) {
                String summary = eventScript.getName() + " " + eventScript.getExitStatus(eventScript.getExitCode());
                GetMethod httpGet = new GetMethod(EVENT_PUBLISHING_URL);
                httpGet.setQueryString(new NameValuePair[]{
                        new NameValuePair(QUERY_STRING_TYPE, EVENT_TYPE_INFO),
                        new NameValuePair(QUERY_STRING_SUMMARY, summary)
                });
                try {
                    int statusCode = httpClient.executeMethod(httpGet);
                    if (statusCode >= 200 && statusCode < 300) {
                        eventScript.setStatusChanged(false);
                        LOG.info("Event " + eventScript.getName() + " published to controller");
                    } else {
                        String response = httpGet.getResponseBodyAsString();
                        LOG.error("Unexpected response : " + response);
                    }
                } catch (IOException e) {
                    LOG.error("Error while posting event to controller", e);
                } finally {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Releasing the connection");
                    }
                    httpGet.releaseConnection();
                }
            }
        }
    }
}
