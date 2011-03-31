/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.xml.DOMConfigurator;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.properties.Properties;

public class LoggerFactory {

    public static final String LOG4J_DOM_CONFIG_PROPERTY = "oxf.log4j-config";
    public static final String LOG4J_DOM_CONFIG_PROPERTY_OLD = "oxf.servlet.log4j";
    
    static
    {
    	// 11-22-2004 d : Current log4j tries to load a default config. This is
    	//                why we are seeing a message about a log4j.properties being
    	//                loaded from the Axis jar.  
    	//                Since this isn't a behaviour we want we hack around it by
    	//                specifying a file that doesn't exist.
        // 2008-05-05 a : It is clear if this solves a problem with an older version of
        //                Axis we were shipping with Orbeon Forms back in 2004, or a more
        //                complex interaction with a particular application server.
        //                We don't think this is relevant anymore and are commenting this out.
        //                Also see this thread on ops-users:
        //                http://www.nabble.com/Problem-with-log-in-orbeon-with-multiple-webapp-tt16932990.html

        // System.setProperty( "log4j.configuration", "-there-aint-no-such-file-" );
    }

    private static final Logger logger = LoggerFactory.createLogger(LoggerFactory.class);

    public static Logger createLogger(Class clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /*
     * Init basic config until resource manager is setup.
     */
    public static void initBasicLogger() {
        // 2008-07-25 a This has been here for a long time and it is not clear why it was put there. But this doesn't
        //              seem to be a good idea, and is causing some problem. So: commenting. See discussion in this thread:
        //              http://www.nabble.com/Problem-with-log-in-orbeon-with-multiple-webapp-to16932990.html#a18661451
        // LogManager.resetConfiguration();
        Logger root = Logger.getRootLogger();
        root.setLevel(Level.INFO);
        root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN), ConsoleAppender.SYSTEM_ERR));
    }

    /**
     * Init log4j. Needs Orbeon Forms Properties system up and running.
     */
    public static void initLogger() {
        try {
            // Accept both xs:string and xs:anyURI types
            String log4jConfigURL = Properties.instance().getPropertySet().getStringOrURIAsString(LOG4J_DOM_CONFIG_PROPERTY);
            if (log4jConfigURL == null)
                log4jConfigURL = Properties.instance().getPropertySet().getStringOrURIAsString(LOG4J_DOM_CONFIG_PROPERTY_OLD);

            if (log4jConfigURL != null) {
                final Processor urlGenerator = PipelineUtils.createURLGenerator(log4jConfigURL, true);
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, domSerializer, ProcessorImpl.INPUT_DATA);
                final PipelineContext pipelineContext = new PipelineContext();
                boolean success = false;
                final org.w3c.dom.Element element;
                try {
                    urlGenerator.reset(pipelineContext);
                    domSerializer.reset(pipelineContext);
                    domSerializer.start(pipelineContext);
                    element = domSerializer.getW3CDocument(pipelineContext).getDocumentElement();
                    success = true;
                } finally {
                    pipelineContext.destroy(success);
                }
                DOMConfigurator.configure(element);
            } else {
                logger.info("Property " + LOG4J_DOM_CONFIG_PROPERTY + " not set. Skipping logging initialization.");
            }
        } catch (Throwable e) {
            logger.error("Cannot load Log4J configuration. Skipping logging initialization", e);
        }
    }
}
