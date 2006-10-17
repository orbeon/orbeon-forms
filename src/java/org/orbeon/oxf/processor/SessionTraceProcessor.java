package org.orbeon.oxf.processor;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.ContentHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 *
 */
public class SessionTraceProcessor extends ProcessorImpl {
    public SessionTraceProcessor() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, final ContentHandler contentHandler) {

                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                final ExternalContext.Session session = externalContext.getSession(true);

                final List sessionTraceInfos = (ArrayList) session.getAttributesMap().get(SessionTrace.SESSION_KEY);

                final ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);

                helper.startDocument();
                helper.startElement("traces");

                if (sessionTraceInfos != null) {
                    for (Iterator i = sessionTraceInfos.iterator(); i.hasNext();) {
                        final SessionTrace.SessionTraceInfos traceInfos = (SessionTrace.SessionTraceInfos) i.next();

                        final StdOutTrace.TraceNode traceNode = StdOutTrace.getTraceNodes(traceInfos.getTraceInfos());
                        final String requestURI = traceInfos.getRequestURI();

                        helper.startElement("trace", new String[] { "requestURI", requestURI != null ? requestURI : "" } );
                        outputTraceNodes(helper, traceNode, 0);
                        helper.endElement();
                    }
                }

                helper.endElement();
                helper.endDocument();

                session.getAttributesMap().remove(SessionTrace.SESSION_KEY);
            }
        };
        addOutput(name, output);
        return output;
    }

    private static void outputTraceNodes(ContentHandlerHelper helper, StdOutTrace.TraceNode traceNode, final int level) {

        final PipelineContext.TraceInfo traceInfo = traceNode.traceInfo;

        helper.startElement("entry", new String[] { "start", Long.toString(traceInfo.start),
                "end", Long.toString(traceInfo.end),
                "level", Integer.toString(level),
                "local-time", Long.toString(traceNode.getLocalTime()),
                "cumulative-time", Long.toString(traceNode.getCumulativeTime()),
                "line", Integer.toString(traceInfo.line),
                "systemId", traceInfo.systemID != null ? traceInfo.systemID : "" } );

        helper.endElement();

        final java.util.Iterator iterator = traceNode.children.iterator();
        final int nextLevel = level + 1;
        while (iterator.hasNext()) {
            final StdOutTrace.TraceNode childTraceNode = (StdOutTrace.TraceNode) iterator.next();
            outputTraceNodes(helper, childTraceNode, nextLevel);
        }
    }
}
