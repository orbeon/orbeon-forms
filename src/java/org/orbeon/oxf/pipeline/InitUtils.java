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
package org.orbeon.oxf.pipeline;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.cache.CacheStatistics;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.XMLProcessorRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.webapp.ServletContextExternalContext;
import org.orbeon.oxf.webapp.WebAppContext;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;



public class InitUtils {
       

    private static final String CACHE_SIZE_PROPERTY = "oxf.cache.size";
    public static final String PROLOGUE_PROPERTY = "oxf.prologue";
    public static final String DEFAULT_PROLOGUE = "oxf:/processors.xml";

    private static Logger logger = LoggerFactory.createLogger(InitUtils.class);

    private static boolean processorDefinitionsInitialized;

    /**
     * Run a processor with an ExternalContext.
     */
    public static void runProcessor(Processor processor, ExternalContext externalContext, PipelineContext pipelineContext) throws Exception {

        // Record start time for this request
        long tsBegin = logger.isInfoEnabled() ? System.currentTimeMillis() : 0;
        String pathInfo = null;
        try {
            ExternalContext.Request request = externalContext.getRequest();
            pathInfo = request.getPathInfo();
        } catch (UnsupportedOperationException e) {
            // Don't do anything
        }

        // Set ExternalContext
        if (externalContext != null) {
            if (logger.isInfoEnabled()) {
                String startLoggerString = externalContext.getStartLoggerString();
                if (startLoggerString != null && startLoggerString.length() > 0)
                    logger.info(startLoggerString);
            }
            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);
        }
        // Make the static context available
        StaticExternalContext.setStaticContext(new StaticExternalContext.StaticContext(externalContext, pipelineContext));

        try {
            // Set cache size
            Integer cacheMaxSize = OXFProperties.instance().getPropertySet().getInteger(CACHE_SIZE_PROPERTY);
            if (cacheMaxSize != null)
                ObjectCache.instance().setMaxSize(pipelineContext, cacheMaxSize.intValue());

            // Start execution
            processor.reset(pipelineContext);
            processor.start(pipelineContext);
            if (!pipelineContext.isDestroyed())
                pipelineContext.destroy(true);
        } catch (Exception e) {
            try {
                if (!pipelineContext.isDestroyed())
                    pipelineContext.destroy(false);
            } catch (Exception f) {
                logger.error("Exception while destroying context after exception", OXFException.getRootThrowable(f));
            }
            LocationData locationData = ValidationException.getRootLocationData(e);
            Throwable throwable = OXFException.getRootThrowable(e);
            String message = locationData == null
                    ? "Exception with no location data"
                    : "Exception at " + locationData.toString();
            logger.error(message, throwable);
            // Make sure the caller can do something about it, like trying to run an error page
            throw e;
        } finally {
            // Free context
            StaticExternalContext.removeStaticContext();

            if (logger.isInfoEnabled()) {
                // Display cache statistics
                CacheStatistics statistics = ObjectCache.instance().getStatistics(pipelineContext);
                int hitCount = statistics.getHitCount();
                int missCount = statistics.getMissCount();
                String successRate = null;
                if (hitCount + missCount > 0)
                    successRate = hitCount * 100 / (hitCount + missCount) + "%";
                else
                    successRate = "N/A";
                long timing = System.currentTimeMillis() - tsBegin;
                logger.info((pathInfo != null ? pathInfo : "Done running processor") + " - Timing: " + timing
                        + " - Cache hits: " + hitCount
                        + ", fault: " + missCount
                        + ", adds: " + statistics.getAddCount()
                        + ", success rate: " + successRate);
            }
        }
    }

    /**
     * Run a processor without ExternalContext. Should rarely be used!
     */
    public static void runProcessor(Processor processor) throws Exception {
       runProcessor(processor, null, new PipelineContext());
    }

    /**
     * Run a processor with a Servlet Context and an optional session.
     *
     * This is used for servlet context and session listeners.
     */
    public static void runProcessor(Processor processor, ServletContext servletContext, HttpSession session) throws Exception {
        ExternalContext externalContext = (servletContext != null) ? new ServletContextExternalContext(servletContext, session) : null;
        runProcessor(processor, externalContext, new PipelineContext());
    }

    /**
     * Create a processor and connect its inputs to static URLs.
     */
    public static Processor createProcessor(ProcessorDefinition processorDefinition) {
        PipelineContext pipelineContext = new PipelineContext();
        Processor processor;
        if (processorDefinition.getName() != null) {
            processor = ProcessorFactoryRegistry.lookup(processorDefinition.getName()).createInstance(pipelineContext);
        } else {
            processor = ProcessorFactoryRegistry.lookup(processorDefinition.getUri()).createInstance(pipelineContext);
        }
        for (java.util.Iterator i = processorDefinition.getEntries().keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            Object o = processorDefinition.getEntries().get(name);

            if (o instanceof String) {
                String url = (String) o;
                Processor urlGenerator = PipelineUtils.createURLGenerator(url);
                PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, processor, name);
            } else if (o instanceof Element) {
                final Element elt = ( Element )o;
                final LocationData ld = ( LocationData )elt.getData();
                final String lsid = ld == null ? null : ld.getSystemID();
                final String sid = lsid == null ? DOMGenerator.DefaultContext : lsid;
                DOMGenerator domGenerator = PipelineUtils.createDOMGenerator
                    ( elt, "init input", DOMGenerator.ZeroValidity, sid );
                PipelineUtils.connect(domGenerator, ProcessorImpl.OUTPUT_DATA, processor, name);
            } else
                throw new IllegalStateException("Incorrect type in map.");
        }
        return processor;
    }

    public static void run(ServletContext servletContext, HttpSession session, String uriNamePropertyPrefix, String processorInputProperty) throws Exception {

        // Make sure the Web app context is initialized
        try {
            WebAppContext.instance(servletContext);
        } catch (Exception e) {
            Throwable rootThrowable = OXFException.getRootThrowable(e);
            logger.error("Error initializing the WebAppContext", rootThrowable);
            throw new OXFException(rootThrowable);
        }

        // Try to obtain a processor definition from the properties
        ProcessorDefinition  processorDefinition
                = getDefinitionFromProperties(uriNamePropertyPrefix, processorInputProperty);

        // Try to obtain a processor definition from the context
        if (processorDefinition == null)
            processorDefinition = getDefinitionFromServletContext(servletContext, uriNamePropertyPrefix, processorInputProperty);

        // Create and run processor
        if (processorDefinition != null) {
            Processor processor = createProcessor(processorDefinition);
            runProcessor(processor, servletContext, session);
        }
        // Otherwise, just don't do anything
    }

    /**
     * Register processor definitions with the default XML Processor Registry. This defines the
     * mapping of processor names to class names
     */
    public static synchronized void initializeProcessorDefinitions() {
        if (!processorDefinitionsInitialized) {
            synchronized (PipelineEngineImpl.class) {
                if (!processorDefinitionsInitialized) {
                    // Register inital processors with the default XML Processor Registry
                    Processor processorDefinitions = PipelineUtils.createURLGenerator(DEFAULT_PROLOGUE);
                    Processor registry = new XMLProcessorRegistry();
                    PipelineUtils.connect(processorDefinitions, "data", registry, "config");

                    PipelineContext pipelineContext = new PipelineContext();
                    registry.reset(pipelineContext);
                    registry.start(pipelineContext);

                    // If user defines a PROLOGUE_PROPERTY, overrides the defaults
                    String prologueSrc = OXFProperties.instance().getPropertySet().getString(PROLOGUE_PROPERTY);
                    if (prologueSrc != null) {
                        processorDefinitions = PipelineUtils.createURLGenerator(prologueSrc);
                        registry = new XMLProcessorRegistry();
                        PipelineUtils.connect(processorDefinitions, "data", registry, "config");

                        pipelineContext = new PipelineContext();
                        registry.reset(pipelineContext);
                        registry.start(pipelineContext);
                    }

                    processorDefinitionsInitialized = true;
                }
            }
        }
    }

    public static java.util.Map getContextInitParametersMap(ServletContext servletContext) {
        java.util.Map contextInitParameters = new java.util.HashMap();
        for (java.util.Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            contextInitParameters.put(name, servletContext.getInitParameter(name));
        }
        return java.util.Collections.unmodifiableMap(contextInitParameters);
    }

    public static ProcessorDefinition getDefinitionFromServletContext(ServletContext servletContext, String uriNamePropertyPrefix, String inputPropertyPrefix) {
        return getDefinitionFromMap(new ServletContextInitMap(servletContext), uriNamePropertyPrefix, inputPropertyPrefix);
    }

    public static ProcessorDefinition getDefinitionFromProperties(String uriNamePropertyPrefix, String inputPropertyPrefix) {
        return getDefinitionFromMap(new OXFPropertiesMap(), uriNamePropertyPrefix, inputPropertyPrefix);
    }

    /**
     * Create a ProcessorDefinition from a Map. Only Map.get() and Map.keySet() are used.
     */
    public static ProcessorDefinition getDefinitionFromMap(java.util.Map map, String uriNamePropertyPrefix, String inputPropertyPrefix) {
        ProcessorDefinition processorDefinition = null;
        String processorURI = (String) map.get(uriNamePropertyPrefix + "uri");
        Object processorName = map.get(uriNamePropertyPrefix + "name");

        if (processorURI != null || processorName != null) {
            processorDefinition = new ProcessorDefinition();
            processorDefinition.setUri(processorURI);
            // Support both xs:string or xs:QName for processor name
            processorDefinition.setName((processorName instanceof String) ? XMLUtils.explodedQNameToQName((String) processorName) : (QName) processorName);
            for (java.util.Iterator i = map.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                if (name.startsWith(inputPropertyPrefix)) {
                    Object value = map.get(name);
                    // Support both xs:string and xs:anyURI for processor input
                    String stringValue = (value instanceof String) ? (String) value : ((java.net.URL) value).toExternalForm();
                    processorDefinition.addInput(name.substring(inputPropertyPrefix.length()), stringValue);
                }
            }
            logger.debug("Created processor definition from Servlet context parameters.");
        }
        return processorDefinition;
    }

    /**
     * Present a read-only view of the properties as a Map.
     */
    public static class OXFPropertiesMap extends AttributesToMap {
        public OXFPropertiesMap() {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return OXFProperties.instance().getPropertySet().getObject(s);
                }

                public java.util.Enumeration getAttributeNames() {
                    return java.util.Collections.enumeration(OXFProperties.instance().getPropertySet().keySet());
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, Object o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }

    /**
     * Present a read-only view of the ServletContext initialization parameters as a Map.
     */
    public static class ServletContextInitMap extends AttributesToMap {
        public ServletContextInitMap(final ServletContext servletContext) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return servletContext.getInitParameter(s);
                }

                public java.util.Enumeration getAttributeNames() {
                    return servletContext.getInitParameterNames();
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, Object o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }

    /**
     * Present a view of the HttpSession properties as a Map.
     */
    public static class SessionMap extends AttributesToMap {
        public SessionMap(final HttpSession httpSession) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return httpSession.getAttribute(s);
                }

                public java.util.Enumeration getAttributeNames() {
                    return httpSession.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    httpSession.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    httpSession.setAttribute(s, o);
                }
            });
        }
    }

    /**
     * Present a view of the HttpServletRequest properties as a Map.
     */
    public static class RequestMap extends AttributesToMap {
        public RequestMap(final HttpServletRequest httpServletRequest) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return httpServletRequest.getAttribute(s);
                }

                public java.util.Enumeration getAttributeNames() {
                    return httpServletRequest.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    httpServletRequest.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    httpServletRequest.setAttribute(s, o);
                }
            });
        }
    }

}
