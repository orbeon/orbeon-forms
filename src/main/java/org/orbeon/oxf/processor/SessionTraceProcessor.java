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
package org.orbeon.oxf.processor;

/**
 *
 */
//public class SessionTraceProcessor extends ProcessorImpl {
//    public SessionTraceProcessor() {
//        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
//    }
//
//    public ProcessorOutput createOutput(String name) {
//        final ProcessorOutput output = new ProcessorOutputImpl(getClass(), name) {
//            public void readImpl(PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
//
//                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//                final ExternalContext.Session session = externalContext.getSession(true);
//
//                final List sessionTraceInfos = (ArrayList) session.getAttributesMap().get(SessionTrace.SESSION_KEY);
//
//                final ContentHandlerHelper helper = new ContentHandlerHelper(xmlReceiver);
//
//                helper.startDocument();
//                helper.startElement("traces");
//
//                if (sessionTraceInfos != null) {
//                    for (Iterator i = sessionTraceInfos.iterator(); i.hasNext();) {
//                        final SessionTrace.SessionTraceInfos traceInfos = (SessionTrace.SessionTraceInfos) i.next();
//
//                        final StdOutTrace.TraceNode traceNode = StdOutTrace.buildTraceNodes(traceInfos.getTraceInfos());
//                        final String requestURI = traceInfos.getRequestURI();
//
//                        helper.startElement("trace", new String[] { "requestURI", requestURI != null ? requestURI : "" } );
//                        outputTraceNodes(helper, traceNode, 0);
//                        helper.endElement();
//                    }
//                }
//
//                helper.endElement();
//                helper.endDocument();
//
//                session.getAttributesMap().remove(SessionTrace.SESSION_KEY);
//            }
//        };
//        addOutput(name, output);
//        return output;
//    }
//
//    private static void outputTraceNodes(ContentHandlerHelper helper, StdOutTrace.TraceNode traceNode, final int level) {
//
//        final TraceEntry traceEntry = traceNode.traceEntry;
//
//        helper.startElement("entry", new String[] { "start", Long.toString(traceEntry.start),
//                "end", Long.toString(traceEntry.end),
//                "level", Integer.toString(level),
//                "local-time", Long.toString(traceNode.getLocalTime()),
//                "cumulative-time", Long.toString(traceNode.getCumulativeTime()),
//                "line", Integer.toString(traceEntry.line),
//                "systemId", traceEntry.systemId != null ? traceEntry.systemId : "" } );
//
//        helper.endElement();
//
//        final java.util.Iterator iterator = traceNode.children.iterator();
//        final int nextLevel = level + 1;
//        while (iterator.hasNext()) {
//            final StdOutTrace.TraceNode childTraceNode = (StdOutTrace.TraceNode) iterator.next();
//            outputTraceNodes(helper, childTraceNode, nextLevel);
//        }
//    }
//}
