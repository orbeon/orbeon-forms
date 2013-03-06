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

import org.orbeon.oxf.util.PropertyContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private Map<Object, Object> attributes = new HashMap<Object, Object>();
    private List<ContextListener> listeners;
    private boolean destroyed;

    private static ThreadLocal<PipelineContext> threadLocal = new ThreadLocal<PipelineContext>();
    private PipelineContext originalPipelineContext;

    /**
     * Create a new pipeline context.
     */
    public PipelineContext() {
        // Save and set ThreadLocal
        originalPipelineContext = threadLocal.get();
        threadLocal.set(this);
    }

    public static PipelineContext get() {
        return threadLocal.get();
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
