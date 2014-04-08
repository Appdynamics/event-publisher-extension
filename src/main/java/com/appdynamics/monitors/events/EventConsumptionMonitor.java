package com.appdynamics.monitors.events;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class EventConsumptionMonitor extends AManagedMonitor {

    private static final Logger LOG = Logger.getLogger(EventConsumptionMonitor.class);
    private static final int WORKER_COUNT = 5; // Max worker count can be altered via configuration.
    private static final String EVENT_PUBLISHING_URL = "http://localhost:8293/machineagent/event";
    private static final String QUERY_STRING_TYPE = "type";
    private static final String QUERY_STRING_SUMMARY = "summary";
    private static final String EVENT_TYPE_INFO = "info";

    private Integer workerCount = WORKER_COUNT;

    private Collection<EventScript> eventScripts; // Collection of tasks (implementing java.lang.Runnable)


    public void parseXML(final String xml) throws DocumentException {
        eventScripts = new LinkedList<EventScript>();

        final SAXReader reader = new SAXReader();
        final Document document = reader.read(xml);
        final Element root = document.getRootElement();

        for (Iterator<Element> i = root.elementIterator(); i.hasNext(); ) {
            Element element = i.next();

            if (element.getName().equals("event")) {
                Iterator<Element> elementIterator = element.elementIterator();

                final EventScript script = new EventScript();
                for (Iterator<Element> j = elementIterator; j.hasNext(); ) {
                    element = j.next();
                    if (element.getName().equals("name")) {
                        script.setName(element.getText());
                    } else if (element.getName().equals("summary")) {
                        script.setSummary(element.getText());
                    } else if (element.getName().equals("comment")) {
                        script.setComment(element.getText());
                    } else if (element.getName().equals("path")) {
                        script.setPath(element.getText());
                    } else if (element.getName().equals("period")) {
                        script.setPeriod(Integer.parseInt(element.getText()));
                    } else if (element.getName().equals("arguments")) {
                        script.setArguments(element.getText());
                    } else if (element.getName().equals("max-wait-time")) {
                        script.setMaxWaitTime(Integer.parseInt(element.getText()));
                    } else if (element.getName().equals("outputs")) {
                        Iterator<Element> outputsIterator = element.elementIterator();
                        for (; outputsIterator.hasNext(); ) {
                            element = outputsIterator.next();
                            if (element.getName().equals("output")) {
                                List<Element> outputs = element.elements();
                                script.addOutput(Integer.parseInt(outputs.get(0).getText()), outputs.get(1).getText());
                            }
                        }
                    }
                }

                script.setId(UUID.randomUUID().toString());
                eventScripts.add(script);
            }
        }
    }

    /**
     * @see com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map, com.singularity.ee.agent.systemagent.api.TaskExecutionContext)
     */
    public TaskOutput execute(final Map<String, String> args, final TaskExecutionContext context)
            throws TaskExecutionException {

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
        try {
            parseXML(projectFolder + "/events.xml");
        } catch (DocumentException e) {
            LOG.error("Failed to parse XML." + e.toString());
        }

        final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(this.workerCount);

        // Add the tasks to execute them in some time in future.
        for (final EventScript script : eventScripts) {
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

        //As this is continuous extension, make this thread wait indefinitely.
        CountDownLatch infiniteWait = new CountDownLatch(1);
        try {
            infiniteWait.await();   //Will make this thread to wait till the CountDownLatch reaches to 0.
        } catch (InterruptedException e) {
            LOG.error("Failed to wait indefinitely ", e);
        }

        return new TaskOutput("Finished executing Nagios Monitor");
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

    public static void main(String[] arg) throws DocumentException, TaskExecutionException {
        EventConsumptionMonitor eventConsumptionMonitor = new EventConsumptionMonitor();

        Map<String, String> args = new HashMap<String, String>();
        args.put("event-xml", "/home/satish/AppDynamics/Code/extensions/event-consumption-extension/src/main/resources/config/events.xml");
        eventConsumptionMonitor.execute(args, null);
    }

}
