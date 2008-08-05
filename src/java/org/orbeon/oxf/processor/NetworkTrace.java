/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.PipelineContext.Trace;
import org.orbeon.oxf.pipeline.api.PipelineContext.TraceInfo;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NetworkTrace implements Trace {

    static private Logger logger = LoggerFactory.createLogger(NetworkTrace.class);

    private PipelineContext pipelineContext;

    public NetworkTrace() {
        if (logger.isDebugEnabled())
            logger.debug("Creating network trace object: " + hashCode());
    }

    static {

    }

    final List traceInfos = new ArrayList(0);

    public void setPipelineContext(PipelineContext pipelineContext) {
        this.pipelineContext = pipelineContext;
    }

    public void add(final TraceInfo traceInfo) {
        traceInfos.add(traceInfo);
    }

    public void contextDestroyed(final boolean success) {
        if (logger.isDebugEnabled())
            logger.debug("Destroying network trace object: " + hashCode());

        final String host;
        final int port;
        {
            final Properties properties = Properties.instance();
            final PropertySet propertySet = properties.getPropertySet();
            final String traceHost = propertySet.getNMTOKEN("processor.trace.host");
            host = traceHost == null ? "localhost" : traceHost;
            final Integer tracePortInteger = propertySet.getNonNegativeInteger("processor.trace.port");
            port = tracePortInteger == null ? 9191 : tracePortInteger.intValue();
        }

//        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//        String requestURI;
//        try {
//            requestURI = externalContext.getRequest().getRequestURI();
//        } catch (UnsupportedOperationException e) {
//            requestURI = "";
//        }

        final java.util.Iterator iterator = traceInfos.iterator();
        if (iterator.hasNext()) {
            java.io.ObjectOutputStream oos = null;
            Throwable throwable = null;

            try {
                final java.net.Socket socket = new java.net.Socket(host, port);
                final java.io.OutputStream os = socket.getOutputStream();
                oos = new java.io.ObjectOutputStream(os);
                oos.writeShort(2501); // magic
                oos.writeShort(1); // version
                while (iterator.hasNext()) {
                    final TraceInfo traceInfo = (TraceInfo) iterator.next();
                    final String systemId = traceInfo.systemID != null ? traceInfo.systemID : "";
                    oos.writeObject(systemId);
                    oos.writeInt(traceInfo.line);
                    oos.writeLong(traceInfo.start);
                    oos.writeLong(traceInfo.end);

                }
            } catch (final java.net.UnknownHostException e) {
                throwable = e;
            } catch (final java.io.IOException e) {
                throwable = e;
            }
            finally {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (final java.io.IOException e) {
                        // noop
                    }
                }
            }
            if (throwable != null) throw new OXFException(throwable);
        }
    }
}
