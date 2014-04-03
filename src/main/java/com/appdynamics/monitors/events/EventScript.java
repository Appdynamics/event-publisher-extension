package com.appdynamics.monitors.events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

public class EventScript implements Runnable {

    private static final Logger LOG = Logger.getLogger(EventScript.class);
    private static final String OUTPUT_NOT_DEFINED = "OUTPUT_NOT_DEFINED";
    
    private String id;
    private String name;
    private String summary;
    private String comment;
    private List<Output> outputs;
    private String path;
    private String arguments;
    private int period;
    private int maxWaitTime;
    private boolean statusChanged;
    private int exitCode = -1;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public void addOutput(int exitCode, String status) {
        if(getOutputs() == null) {
            outputs = new ArrayList<Output>();
        }
        outputs.add(new Output(exitCode, status));
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Output> outputs) {
        this.outputs = outputs;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    public void setMaxWaitTime(int maxWaitTime) {
        if(maxWaitTime <= 0) {
            maxWaitTime = 5;  //If not specified, wait for 5 seconds
        }
        this.maxWaitTime = maxWaitTime;
    }

    public boolean isStatusChanged() {
        return statusChanged;
    }

    public void setStatusChanged(boolean statusChanged) {
        this.statusChanged = statusChanged;
    }

    public int getExitCode() {
        return exitCode;
    }
    
    public String getExitStatus(int exitCode) {
        for(Output output : outputs) {
          if(output.getExitCode() == exitCode) {
              return output.getStatus();
          }
        }
        return OUTPUT_NOT_DEFINED;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public void run() {
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            process = runtime.exec(this.path);
        } catch (IOException e) {
            LOG.error("Unable to run script: " + this.name, e);
        }

        CountDownLatch doneSignal = new CountDownLatch(1);
        Worker worker = new Worker(this.name, process, doneSignal);
        worker.start();
        try {
            doneSignal.await(this.maxWaitTime, TimeUnit.SECONDS);

            if (worker.getExitCode() != null) {
                if(this.exitCode != worker.getExitCode()) {
                    this.exitCode = worker.getExitCode();
                    this.statusChanged = true;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exit code for script : " + this.name + " is :" + this.exitCode);
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No Exit code for script : " + this.name);
                }
            }
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            LOG.error("Unable to run script: " + this.name, e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    class Output {
        private int exitCode;
        private String status;

        Output(int exitCode, String status) {
            this.exitCode = exitCode;
            this.status = status;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStatus() {
            return status;
        }
    }

    class Worker extends Thread {
        private final Process process;
        private Integer exitCode;
        private final CountDownLatch countDownLatch;
        private final String scriptName;

        Worker(String scriptName, Process process, CountDownLatch countDownLatch) {
            this.process = process;
            this.countDownLatch = countDownLatch;
            this.scriptName = scriptName;
        }

        public void run() {
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            } finally {
                countDownLatch.countDown();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exit code from worker for script : " + this.scriptName + " is :" + this.exitCode);
                }
            }
        }

        public Integer getExitCode() {
            return exitCode;
        }
    }
}
