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
package org.orbeon.oxf.util;

import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.resources.OXFProperties;

public class LoggerFactory {

    public static final String LOG4J_DOM_CONFIG_PROPERTY = "oxf.log4j-config";
    public static final String LOG4J_DOM_CONFIG_PROPERTY_OLD = "oxf.servlet.log4j";
    
    static 
    {
    	// 11-22-2004 d : Current log4j tries to load a default config. This is
    	//                why we are seeing a message about a log4j.properties being
    	//                loaded from the Axis jar.  
    	//                Since this is't a behaviour we want we hack around it by
    	//                specifying a file that doesn't exist.
    	System.setProperty( "log4j.configuration", "-there-aint-no-such-file-" );
    }

    private static final Logger logger = LoggerFactory.createLogger(LoggerFactory.class);

    public static Logger createLogger(Class clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /*
     * Init basic config until resource manager is setup.
     */
    public static void initBasicLogger() {
        LogManager.resetConfiguration();
        Logger root = Logger.getRootLogger();
        root.setLevel(Level.INFO);
        root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN), ConsoleAppender.SYSTEM_ERR));
    }

    /**
     * Init log4j. Needs Presentation Server Properties system up and running.
     */
    public static void initLogger() {
        try {
            // Accept both xs:string and xs:anyURI types
            String log4jConfigURL = OXFProperties.instance().getPropertySet().getStringOrURIAsString(LOG4J_DOM_CONFIG_PROPERTY);
            if (log4jConfigURL == null)
                log4jConfigURL = OXFProperties.instance().getPropertySet().getStringOrURIAsString(LOG4J_DOM_CONFIG_PROPERTY_OLD);

            if (log4jConfigURL != null) {
                Processor url = PipelineUtils.createURLGenerator(log4jConfigURL);
                DOMSerializer dom = new DOMSerializer();
                PipelineUtils.connect(url, ProcessorImpl.OUTPUT_DATA, dom, ProcessorImpl.INPUT_DATA);
                PipelineContext ctx = new PipelineContext();
                dom.reset(ctx);
                dom.start(ctx);
                Object o = dom.getW3CDocument(ctx).getDocumentElement();
                DOMConfigurator.configure( ( org.w3c.dom.Element )o );
            } else {
                logger.info("Property " + LOG4J_DOM_CONFIG_PROPERTY + " not set. Skipping logging initialization.");
            }
        } catch (Throwable e) {
            logger.error("Cannot load Log4J configuration. Skipping logging initialization", e);
        }
    }
}
