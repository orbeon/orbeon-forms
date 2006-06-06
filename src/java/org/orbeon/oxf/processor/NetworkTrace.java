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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext.Trace;
import org.orbeon.oxf.pipeline.api.PipelineContext.TraceInfo;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.OXFProperties.PropertySet;
import org.orbeon.oxf.util.LoggerFactory;
import org.apache.log4j.Logger;

public class NetworkTrace implements Trace {

    private static final String host;
    private static final int port;

    static private Logger logger = LoggerFactory.createLogger(NetworkTrace.class);

    public NetworkTrace() {
        if (logger.isDebugEnabled())
            logger.debug("Creating network trace object: " + hashCode());
    }

    static {
        final OXFProperties prps = OXFProperties.instance();
        final PropertySet prpSet = prps.getPropertySet();
        final String hst = prpSet.getNMTOKEN("processor.trace.host");
        host = hst == null ? "localhost" : hst;
        final Integer prtInt = prpSet.getNonNegativeInteger("processor.trace.port");
        port = prtInt == null ? 9191 : prtInt.intValue();
    }

    final java.util.ArrayList traceInfs = new java.util.ArrayList(0);

    public void add(final TraceInfo tinf) {
        traceInfs.add(tinf);
    }

    public void contextDestroyed(final boolean scss) {
        if (logger.isDebugEnabled())
            logger.debug("Destroying network trace object: " + hashCode());
        final java.util.Iterator itr = traceInfs.iterator();
        if (itr.hasNext()) {
            java.io.ObjectOutputStream oos = null;
            Throwable t = null;

            try {
                final java.net.Socket sckt = new java.net.Socket(host, port);
                final java.io.OutputStream os = sckt.getOutputStream();
                oos = new java.io.ObjectOutputStream(os);
                oos.writeShort(2501); // magic
                oos.writeShort(1); // version
                while (itr.hasNext()) {
                    final TraceInfo tinf = (TraceInfo) itr.next();
                    oos.writeObject(tinf.systemID != null ? tinf.systemID : "");
                    oos.writeInt(tinf.line);
                    oos.writeLong(tinf.start);
                    oos.writeLong(tinf.end);

                }
            } catch (final java.net.UnknownHostException e) {
                t = e;
            } catch (final java.io.IOException e) {
                t = e;
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
            if (t != null) throw new OXFException(t);
        }
    }
}
