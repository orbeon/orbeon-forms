/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.task.Task;
import org.orbeon.oxf.util.task.TaskScheduler;
import org.orbeon.oxf.webapp.ServletContextExternalContext;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SchedulerProcessor extends ProcessorImpl {

    private static final Logger logger = LoggerFactory.createLogger(SchedulerProcessor.class);

    public static final String SCHEDULER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/scheduler";

    public SchedulerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SCHEDULER_CONFIG_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        try {
            List configs = (List) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    List configs = new ArrayList();
                    Document document = readInputAsDOM4J(context, input);

                    for (Iterator i = XPathUtils.selectIterator(document, "/config/start-task"); i.hasNext();) {
                        Element startTaskElement = (Element) i.next();
                        Config config = new Config(Config.START);
                        config.setName(XPathUtils.selectStringValueNormalize(startTaskElement, "name"));

                        // Create new processor definition
                        ProcessorDefinition processorDefinition = new ProcessorDefinition();
                        config.setProcessorDefinition(processorDefinition);

                        {
                            // Use processor QName
                            final Element processorNameElement = startTaskElement.element(new QName("processor-name"));
                            final QName processorQName = Dom4jUtils.extractTextValueQName(processorNameElement, true);
                            processorDefinition.setName(processorQName);

                            for (final Iterator j = XPathUtils.selectIterator(startTaskElement, "input"); j.hasNext();) {
                                Element inputElement = (Element) j.next();
                                String name = inputElement.attributeValue("name");
                                String url = inputElement.attributeValue("url");
                                if (url != null) {
                                    processorDefinition.addInput(name, url);
                                } else {
                                    final Iterator it = inputElement.elementIterator();
                                    if (it.hasNext()) {
                                        final Element srcElt = (Element) it.next();
                                        final Element elt = (Element) srcElt.clone();
                                        processorDefinition.addInput(name, elt);
                                    } else
                                        throw new OXFException("Node not found input element");
                                }
                            }
                        }

                        String startTimeString = XPathUtils.selectStringValueNormalize(startTaskElement, "start-time");
                        long startTime = 0;
                        if ("now".equalsIgnoreCase(startTimeString)) {
                            startTime = System.currentTimeMillis();
                        } else {
                            startTime = ISODateUtils.parseDate(startTimeString).getTime();
                        }
                        config.setStartTime(startTime);

                        String interval = XPathUtils.selectStringValueNormalize(startTaskElement, "interval");
                        try {
                            config.setInterval(Long.parseLong(interval));
                        } catch (NumberFormatException e) {
                            throw new OXFException("Unsupported long value", e);
                        }

                        String sync = XPathUtils.selectStringValueNormalize(startTaskElement, "synchronized");
                        config.setSynchro(Boolean.valueOf(sync).booleanValue());

                        configs.add(config);
                    }

                    for (Iterator i = XPathUtils.selectIterator(document, "/config/stop-task"); i.hasNext();) {
                        Element el = (Element) i.next();
                        Config config = new Config(Config.STOP);
                        config.setName(XPathUtils.selectStringValueNormalize(el, "name"));
                        configs.add(config);
                    }
                    return configs;
                }
            });

            TaskScheduler scheduler = TaskScheduler.getInstance();
            ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            //assert externalContext != null;

            for (Iterator i = configs.iterator(); i.hasNext();) {
                Config config = (Config) i.next();
                switch (config.getAction()) {
                    case Config.START:
                        // Create processor and connect its inputs
                        Processor processor = InitUtils.createProcessor(config.getProcessorDefinition());
                        processor.setId(config.getName());

                        // Create and schedule a task
                        // The ExternalContext passed has limited visibility on the application context only
                        ProcessorTask task = new ProcessorTask(config.getName(), processor, config.isSynchro(), new ServletContextExternalContext(externalContext));
                        task.setSchedule(config.getStartTime(), config.getInterval());
                        scheduler.schedule(task);
                        break;
                    case Config.STOP:
                        // Find task and cancel it
                        Task[] tasks = scheduler.getRunningTasks();
                        for (int ti = 0; ti < tasks.length; ti++) {
                            if (tasks[ti] instanceof ProcessorTask && (tasks[ti]).getName().equals(config.getName()))
                                tasks[ti].cancel();
                        }
                        break;
                }
            }

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }


    private static class ProcessorTask extends Task {
        private final static String RUNNING = "running";
        private final static String WAITING = "waiting";

        private Processor processor;
        private ExternalContext externalContext;
        private String name;
        private String status = WAITING;
        private boolean sync;

        public ProcessorTask(String name, Processor processor, boolean sync, ExternalContext externalContext) {
            this.name = name;
            this.processor = processor;
            this.sync = sync;
            this.externalContext = externalContext;
        }

        public String getName() {
            return name;
        }

        synchronized public String getStatus() {
            return status;
        }

        synchronized public void setStatus(boolean running) {
            if (running)
                status = RUNNING;
            else
                status = WAITING;
        }

        public void run() {
            try {
                if (sync && getStatus().equals(RUNNING)) {
                    if (logger.isInfoEnabled())
                        logger.info("Task: " + getName() + " won't run since it is already running");
                } else {
                    setStatus(true);
                    InitUtils.runProcessor(processor, externalContext, new PipelineContext(), logger);
                    setStatus(false);
                }
            } catch (Exception e) {
                setStatus(false);
                throw new OXFException(e);
            }
        }
    }

    private static class Config {
        public static final int START = 0;
        public static final int STOP = 1;

        private int action;
        private String name;
        private ProcessorDefinition processorDefinition;
        private long startTime;
        private long interval;
        private boolean synchro = true;

        public Config(int action) {
            this.action = action;
        }

        public int getAction() {
            return action;
        }

        public void setAction(int action) {
            this.action = action;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }


        public boolean isSynchro() {
            return synchro;
        }

        public void setSynchro(boolean synchro) {
            this.synchro = synchro;
        }

        public ProcessorDefinition getProcessorDefinition() {
            return processorDefinition;
        }

        public void setProcessorDefinition(ProcessorDefinition processorDefinition) {
            this.processorDefinition = processorDefinition;
        }
    }
}
