/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.pipeline.api;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.PropertyContext;

import java.util.*;

/**
 * PipelineContext represents a context object passed to all the processors running in a given
 * pipeline session.
 */
public class PipelineContext implements PropertyContext {

    /**
     * Key name for the EXTERNAL_CONTEXT attribute of type ExternalContext.
     */
    public static final String EXTERNAL_CONTEXT = "external-context";

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
     * ContextListener adapter class to facilitate implementations of the ContextListener interface.
     */
    public static class ContextListenerAdapter implements ContextListener {
        public void contextDestroyed(boolean success) {
        }
    }

    public interface Trace extends ContextListener {
        void setPipelineContext(PipelineContext pipelineContext);
        TraceEntry getTraceEntry(ProcessorOutputImpl processorOutputImpl);
    }

    private Map<Object, Object> attributes = new HashMap<Object, Object>();
    private List<ContextListener> listeners;
    private boolean destroyed;
    private Trace trace;
    private boolean traceStopped;

    private static ThreadLocal<PipelineContext> threadLocal = new ThreadLocal<PipelineContext>();
    private PipelineContext originalPipelineContext;

    /**
     * Create a new pipeline context.
     */
    public PipelineContext() {
        // Use global property
        startTrace("oxf.pipeline.trace.class");

        // Save and set ThreadLocal
        originalPipelineContext = threadLocal.get();
        threadLocal.set(this);
    }

    public static PipelineContext get() {
        return threadLocal.get();
    }

    /**
     * Start a trace using the class from the given global property name if present.
     *
     * @param propertyName  property name containing a class name
     * @return              true iif new trace was started
     */
    public boolean startTrace(String propertyName) {
        if (trace == null) { // don't create if already present
            final Properties properties = Properties.instance();
            trace = createTraceIfNeeded(properties, propertyName);
            return trace != null;
        } else {
            return false;
        }
    }

    /**
     * Stop the trace for the remaining duration of this context.
     */
    public void stopTrace() {
        if (trace != null)
            traceStopped = true;
    }

    private Trace createTraceIfNeeded(Properties properties, String propertyName) {
        if (properties != null) {
            final PropertySet propertySet = properties.getPropertySet();
            if (propertySet != null) {
                final String traceClass = propertySet.getNCName(propertyName);
                if (traceClass != null) {
                    return createTrace(traceClass);
                }
            }
        }
        return null;
    }

    private Trace createTrace(String traceClass) {
        Trace trace;
        try {
            final Class clazz = Class.forName(traceClass);
            trace = (Trace) clazz.newInstance();
            trace.setPipelineContext(this);
        } catch (final Exception e) {
            throw new OXFException(e);
        }
        return trace;
    }

    /**
     * Return the trace for update if available.
     *
     * @return trace
     */
    public Trace getTraceForUpdate() {
        return traceStopped ? null : trace;
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

            // Restore ThreadLocal
            threadLocal.set(originalPipelineContext);
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
