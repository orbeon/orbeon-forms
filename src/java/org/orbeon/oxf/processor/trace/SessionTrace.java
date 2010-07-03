/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.processor.trace;

import org.orbeon.oxf.pipeline.api.*;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
//public class SessionTrace implements PipelineContext.Trace {
//
//    public static final String SESSION_KEY = "oxf.trace.traceinfos";
//
//    private PipelineContext pipelineContext;
//    private final List traceInfos = new ArrayList(0);
//
//    public void setPipelineContext(PipelineContext pipelineContext) {
//        this.pipelineContext = pipelineContext;
//    }
//
//    public void add(final TraceEntry traceEntry) {
//        traceInfos.add(traceEntry);
//    }
//
//    public void contextDestroyed(boolean success) {
//
//        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//        String requestURI;
//        try {
//            requestURI = externalContext.getRequest().getRequestURI();
//        } catch (UnsupportedOperationException e) {
//            requestURI = "";
//        }
//
//        try {
//            final ExternalContext.Session session = externalContext.getSession(true);
//
//            List sessionTraceInfos = (ArrayList) session.getAttributesMap().get(SESSION_KEY);
//            if (sessionTraceInfos == null) {
//                sessionTraceInfos = new ArrayList();
//                session.getAttributesMap().put(SESSION_KEY, sessionTraceInfos);
//            }
//            sessionTraceInfos.add(new SessionTraceInfos(traceInfos, requestURI));
//
//        } catch (UnsupportedOperationException e) {
//            // ignore
//        }
//    }
//
//    public static class SessionTraceInfos {
//        private String requestURI;
//        private List traceInfos;
//
//        public SessionTraceInfos(List traceInfos, String requestURI) {
//            this.traceInfos = traceInfos;
//            this.requestURI = requestURI;
//        }
//
//        public List getTraceInfos() {
//            return traceInfos;
//        }
//
//        public String getRequestURI() {
//            return requestURI;
//        }
//    }
//}
