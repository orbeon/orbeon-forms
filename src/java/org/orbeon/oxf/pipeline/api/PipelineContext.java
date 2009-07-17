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
package org.orbeon.oxf.pipeline.api;

import java.util.*;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.PropertyContext;

/**
 * PipelineContext represents a context object passed to all the processors running in a given
 * pipeline session.
 */
public class PipelineContext implements PropertyContext {

    public static class TraceInfo {
        public final long start;
        public long end;
        public final String systemID;
        public final int line;

        public TraceInfo(final long start, final String systemID, final int line) {
            this.start = start;
            this.systemID = systemID;
            this.line = line;
        }

        public TraceInfo(final String systemID, final int line) {
            this.start = System.currentTimeMillis();
            this.systemID = systemID;
            this.line = line;
        }

        public String toString() {
            return systemID + ":( " + start + " " + end + " )";
        }
    }

    /**
     * Key name for the EXTERNAL_CONTEXT attribute of type ExternalContext.
     */
    public static final String EXTERNAL_CONTEXT = "external-context";

    /**
     * This is for internal pipeline engine use.
     */
    public static final String PARENT_PROCESSORS = "parent-processors";

    /**
     * Throwable passed set by ProcessorService.
     */
    public static final String THROWABLE = "throwable";

    // TODO: All those definitions should not be in PipelineContext
    public static final String PATH_MATCHERS = "path-matchers"; // used by PageFlowController
    public static final String XSLT_STYLESHEET_URI_LISTENER = "xslt-stylesheet-uri-listener"; // used by XSLTTransformer
    public static final String REQUEST_GENERATOR_CONTEXT = "request-generator-context"; // used by RequestGenerator
    public static final String FILTER_CHAIN = "filter-chain"; // used by ServletFilterGenerator and OPSServletFilter
    public static final String PORTLET_CONFIG = "portlet-config"; // used only for pipelines called within portlets
    public static final String JNDI_CONTEXT = "context"; // used by Delegation processor and related
    public static final String SQL_PROCESSOR_CONTEXT = "sql-processor-context"; // used by SQLProcessor and related
    public static final String DATASOURCE_CONTEXT = "datasource-context"; // used by DatabaseContext

    /**
     * ContextListener interface to listen on PipelineContext events.
     */
    public interface ContextListener {
        /**
         * Called when the context is destroyed.
         *
         * @param success true if the pipeline execution was successful, false otherwise
         */
        public void contextDestroyed(boolean success);
    }

    /**
     * ContextListener adapter class to faciliate implementations of the ContextListener
     * interface.
     */
    public static class ContextListenerAdapter implements ContextListener {
        public void contextDestroyed(boolean success) {
        }
    }

    public interface Trace extends ContextListener {
        void setPipelineContext(PipelineContext pipelineContext);
        void add(final TraceInfo tinf);
    }

    private Map<Object, Object> attributes = new HashMap<Object, Object>();

    private List<ContextListener> listeners;

    private boolean destroyed;

    private final Trace trace;

    public PipelineContext() {
        final Properties properties = org.orbeon.oxf.properties.Properties.instance();
        if (properties != null) {
            final PropertySet propertySet = properties.getPropertySet();
            if (propertySet != null) {
                final String traceClass = propertySet.getNCName("processor.trace");
                if (traceClass == null) {
                    trace = null;
                } else {
                    Throwable t = null;
                    Trace trace = null;
                    try {
                        final Class clazz = Class.forName(traceClass);
                        trace = (Trace) clazz.newInstance();
                        trace.setPipelineContext(this);
                    } catch (final Exception e) {
                        t = e;
                    }
                    this.trace = trace;
                    if (t != null) {
                        throw new OXFException(t);
                    }
                }
            } else {
                trace = null;
            }
        } else {
            trace = null;
        }
    }

    public Trace getTrace() {
        return trace;
    }

    /**
     * Set an attribute in the context.
     *
     * @param key the attribute key
     * @param o   the attribute value to associate with the key
     */
    public synchronized void setAttribute(Object key, Object o) {
        attributes.put(key, o);
    }

    /**
     * Get an attribute in the context.
     *
     * @param key the attribute key
     * @return the attribute value, null if there is no attribute with the given key
     */
    public Object getAttribute(Object key) {
        return attributes.get(key);
    }

    /**
     * Add a new listener to the context.
     *
     * @param listener listener to add
     */
    public synchronized void addContextListener(ContextListener listener) {
        if (listeners == null)
            listeners = new ArrayList<ContextListener>();
        listeners.add(listener);
    }

    /**
     * Destroy the pipeline context. This method must be called on the context whether the pipeline
     * terminated successfully or not.
     *
     * @param success true if the pipeline executed without exceptions, false otherwise
     */
    public void destroy(boolean success) {
        if (!destroyed) {
            try {
                if (trace != null) {
                    trace.contextDestroyed(success);
                }
                if (listeners != null) {
                    for (final ContextListener contextListener: listeners) {
                        contextListener.contextDestroyed(success);
                    }
                }
            } finally {
                destroyed = true;
            }
        }
    }

    /**
     * Check whether this context has been destroyed.
     *
     * @return true if the context has been destroyed, false otherwise
     */
    public boolean isDestroyed() {
        return destroyed;
    }
}
