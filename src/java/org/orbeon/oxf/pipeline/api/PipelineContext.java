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
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.OXFProperties.PropertySet;

/**
 * PipelineContext represents a context object passed to all the processors running in a given
 * pipeline session.
 */
public class PipelineContext {
	
	public static class TraceInfo {
		public final long start;
		public long end;
		public final String systemID;
		public final int line;
		public TraceInfo( final long strt, final String sysID, final int ln ) {
			start = strt;
			systemID = sysID;
			line = ln;
		}
		public TraceInfo( final String sysID, final int ln ) { 
			systemID = sysID;
			line = ln;
			start = System.currentTimeMillis();
		}
		public String toString() {
			return systemID + ":( " + start + " " + end + " )";
		}
	}

    /**
     * Key name for the EXTERNAL_CONTEXT attribute of type ExternalContext.
     */
    public static final String EXTERNAL_CONTEXT = "external-context";

    // Used by ServletFilterGenerator and OXFServletFilter
    public static final String FILTER_CHAIN = "filter-chain";

    // Used only for pipelines called within portlets
    public static final String PORTLET_CONFIG = "portlet-config";

    // Used by Delegation processors
    public static final String JNDI_CONTEXT = "context";

    // Internal pipeline engine use
    public static final String PARENT_PROCESSORS = "parent-processors";

    public static final String THROWABLE = "throwable";
    public static final String LOCATION_DATA = "location-data";

    public static final String REQUEST_GENERATOR_CONTEXT = "request-generator-context";
    public static final String SQL_PROCESSOR_CONTEXT = "sql-processor-context";
    public static final String XSLT_STYLESHEET_URI_LISTENER = "xslt-stylesheet-uri-listener";

    public static final String DATASOURCE_CONTEXT = "datasource-context";

    public static final String REQUEST = "request";
    
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
    	void add( final TraceInfo tinf );
    }

    private Map attributes = new HashMap();

    private List listeners;

    private boolean destroyed;
    
    private final Trace trace;
    
    public PipelineContext() {
        final OXFProperties prps = OXFProperties.instance();
        final PropertySet prpSet = prps.getPropertySet();
        final String trcCls = prpSet.getString( "processor.trace" ); 
        if ( trcCls == null ) {
        	trace = null;
        } else {
            Throwable t = null;
            Trace trc = null;
            try {
		final Class cls = Class.forName( trcCls );
		trc = ( Trace )cls.newInstance();
            } catch ( final ClassNotFoundException e ) {
                t = e;
            } catch ( final InstantiationException e ) {
                t = e;
            } catch ( final IllegalAccessException e ) {
                t = e;
            }
            trace = trc;
            if ( t != null ) {
                throw new OXFException( t );
            }
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
     * @return    the attribute value, null if there is no attribute with the given key
     */
    public Object getAttribute(Object key) {
        return attributes.get(key);
    }

    /**
     * Add a new listener to the context.
     *
     * @param listener
     */
    public synchronized void addContextListener(ContextListener listener) {
        if (listeners == null)
            listeners = new ArrayList();
        listeners.add(listener);
    }

    /**
     * Destroy the pipeline context. This method must be called on the context whether the pipeline
     * terminated successfully or not.
     *
     * @success true if the pipeline executed without exceptions, false otherwise
     */
    public void destroy(boolean success) {
        if (!destroyed) {
        	if ( trace != null ) trace.contextDestroyed( success );
            if (listeners != null) {
                for (Iterator i = listeners.iterator(); i.hasNext();) {
                    ContextListener contextListener = (ContextListener) i.next();
                    contextListener.contextDestroyed(success);
                }
            }
            destroyed = true;
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
