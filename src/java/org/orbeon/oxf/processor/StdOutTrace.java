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

import org.orbeon.oxf.pipeline.api.PipelineContext.Trace;
import org.orbeon.oxf.pipeline.api.PipelineContext.TraceInfo;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class StdOutTrace implements Trace {

    public static class TraceNode {
        final TraceInfo traceInfo;
        final TraceNode parent;
        final List children = new ArrayList(0);

        public TraceNode(final TraceInfo tinf, final TraceNode prnt) {
            traceInfo = tinf;
            parent = prnt;
        }

        public String toString() {
            return getLocalTime() + " " + getCumulativeTime() + " " + traceInfo.systemID + " " + traceInfo.line;
        }

        public long getLocalTime() {
            long localTime = getCumulativeTime();
            final Iterator iterator = children.iterator();
            while (iterator.hasNext()) {
                final TraceNode childTraceNode = (TraceNode) iterator.next();
                final long childCumulativeTime = childTraceNode.traceInfo.end - childTraceNode.traceInfo.start;
                localTime -= childCumulativeTime;
            }
            return localTime;
        }

        public long getCumulativeTime() {
            return traceInfo.end - traceInfo.start;
        }
    }

    private PipelineContext pipelineContext;
    private final List traceInfos = new ArrayList(0);

    public void setPipelineContext(PipelineContext pipelineContext) {
        this.pipelineContext = pipelineContext;
    }

    public void add(final TraceInfo traceInfo) {
        traceInfos.add(traceInfo);
    }

    private static void dumpTraceNodes(final TraceNode traceNode, final int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("  ");
        }
        System.out.println(traceNode);
        final java.util.Iterator itr = traceNode.children.iterator();
        final int nxtLvl = level + 1;
        while (itr.hasNext()) {
            final TraceNode chld = (TraceNode) itr.next();
            dumpTraceNodes(chld, nxtLvl);
        }
    }

    public void contextDestroyed(final boolean success) {
        if (traceInfos != null) {
            dumpTraceNodes(getTraceNodes(traceInfos), 0);
        }
    }

    public static TraceNode getTraceNodes(List traceInfos) {
        final java.util.Iterator iterator = traceInfos.iterator();
        final java.util.LinkedList stack = new java.util.LinkedList();
        final TraceInfo rootTraceInfo = new TraceInfo(Long.MIN_VALUE, "root", -1);
        rootTraceInfo.end = Long.MAX_VALUE;
        final TraceNode traceNode = new TraceNode(rootTraceInfo, null);
        stack.add(traceNode);
        while (iterator.hasNext()) {
            final TraceInfo traceInfo = (TraceInfo) iterator.next();
            TraceNode top = (TraceNode) stack.getLast();
            while (top.traceInfo.end <= traceInfo.start) {
                stack.removeLast();
                top = (TraceNode) stack.getLast();
            }
            final TraceNode tnd = new TraceNode(traceInfo, top);
            top.children.add(tnd);
            stack.add(tnd);
        }
        return traceNode;
    }
}
