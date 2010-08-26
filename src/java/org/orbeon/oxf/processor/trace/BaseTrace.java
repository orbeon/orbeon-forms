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
package org.orbeon.oxf.processor.trace;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.PipelineContext.Trace;
import org.orbeon.oxf.pipeline.api.TraceEntry;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.ContentHandlerHelper;

import java.util.*;

public abstract class BaseTrace implements Trace {

    public static class TraceNode {

        public final TraceEntry traceEntry;
        public final TraceNode parent;
        public final List<TraceNode> children = new ArrayList<TraceNode>(0);

        public TraceNode() {
            this.traceEntry = null;
            this.parent = null;
        }

        public TraceNode(final TraceEntry traceEntry, final TraceNode traceNode) {
            this.traceEntry = traceEntry;
            parent = traceNode;
        }

        public long getLocalTime() {
            return getCumulativeTime() - getChildrenCumulativeTime();
        }

        public long getCumulativeTime() {
            if (traceEntry == null) {
                // Top-level
                return getChildrenCumulativeTime();
            } else {
                return traceEntry.end - traceEntry.start;
            }
        }

        private long getChildrenCumulativeTime() {
            long childrenCumulativeTime = 0;
            for (final TraceNode child : children) {
                final long childCumulativeTime = child.traceEntry.end - child.traceEntry.start;
                childrenCumulativeTime += childCumulativeTime;
            }
            return childrenCumulativeTime;
        }

        public void toXML(PipelineContext pipelineContext, ContentHandlerHelper helper) {

            helper.startElement("node", new String[] {
                    "local-time", Long.toString(getLocalTime()),
                    "cumulative-time", Long.toString(getCumulativeTime()),
                    "start-time", (traceEntry != null) ? Long.toString(traceEntry.start) : null,
                    "read-called", (traceEntry != null) ? Boolean.toString(traceEntry.outputReadCalled) : null,
                    "getKey-called", (traceEntry != null) ? Boolean.toString(traceEntry.outputGetKeyCalled) : null,
                    "null-key", (traceEntry != null && traceEntry.hasNullKey) ? "true" : null
            });

            if (traceEntry != null && traceEntry.output != null) {
                traceEntry.output.toXML(pipelineContext, helper);
            }

            for (final TraceNode childNode : children) {
                childNode.toXML(pipelineContext, helper);
            }

            helper.endElement();
        }
    }

    protected PipelineContext pipelineContext;

    protected final HashMap<ProcessorOutputImpl, TraceEntry> traceEntries = new LinkedHashMap<ProcessorOutputImpl, TraceEntry>();

    public void setPipelineContext(PipelineContext pipelineContext) {
        this.pipelineContext = pipelineContext;
    }

    public abstract void contextDestroyed(final boolean success);

    public TraceEntry getTraceEntry(ProcessorOutputImpl processorOutputImpl) {
        TraceEntry traceEntry = traceEntries.get(processorOutputImpl);
        if (traceEntry != null) {
            return traceEntry;
        } else {
            traceEntry = new TraceEntry(processorOutputImpl);
            traceEntries.put(processorOutputImpl, traceEntry);
        }
        return traceEntry;
    }

    public static TraceNode buildTraceNodes(Collection<TraceEntry> traceEntries) {

        final LinkedList<TraceNode> stack = new LinkedList<TraceNode>();

        final TraceNode rootTraceNode = new TraceNode();
        stack.add(rootTraceNode);

        for (final TraceEntry traceEntry : traceEntries) {
            TraceNode top = stack.getLast();
            while (top.traceEntry != null && top.traceEntry.end <= traceEntry.start) {// top.traceEntry == null for top-level node
                stack.removeLast();
                top = stack.getLast();
            }
            final TraceNode tnd = new TraceNode(traceEntry, top);
            top.children.add(tnd);
            stack.add(tnd);
        }
        return rootTraceNode;
    }
}
