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

public class StdOutTrace implements Trace {
	
	private static class TraceNode {
		final TraceInfo traceInfo;
		final TraceNode parent;
		final java.util.ArrayList children = new java.util.ArrayList( 0 );
		TraceNode( final TraceInfo tinf, final TraceNode prnt ) {
			traceInfo = tinf;
			parent = prnt;
		}
		public String toString() {
			final long cmTm = traceInfo.end - traceInfo.start;
			long myTm = cmTm;
			final java.util.Iterator itr = children.iterator();
			while ( itr.hasNext() ) {
				final TraceNode chld = ( TraceNode )itr.next();
				final long chldCmTm = chld.traceInfo.end - chld.traceInfo.start;
				myTm -= chldCmTm;
			}
			final String ret = myTm + " " + cmTm + " " + traceInfo.systemID + " " + traceInfo.line;
			return ret;
		}
	}
	
	final java.util.ArrayList traceInfs = new java.util.ArrayList( 0 );

	public void add( final TraceInfo tinf ) {
        traceInfs.add( tinf );
	}
	
    private static void dumpTraceNodes( final TraceNode rt, final int lvl ) {
    	for ( int i = 0; i < lvl; i++ ) {
    		System.out.print( "  " );
    	}
    	System.out.println( rt );
    	final java.util.Iterator itr = rt.children.iterator();
    	final int nxtLvl = lvl + 1;
    	while ( itr.hasNext() ) {
    		final TraceNode chld = ( TraceNode )itr.next();
    		dumpTraceNodes( chld, nxtLvl );
    	}
    }
	
	public void contextDestroyed( final boolean scss ) {
    	if ( traceInfs != null ) {
    		final java.util.Iterator itr = traceInfs.iterator();
    		final java.util.LinkedList stck = new java.util.LinkedList();
    		final TraceInfo rtInf = new TraceInfo( Long.MIN_VALUE, "root", -1 );
    		rtInf.end = Long.MAX_VALUE;
    		final TraceNode rt = new TraceNode( rtInf, null );
    		stck.add( rt );
    		while ( itr.hasNext() ) {
    			final TraceInfo tinf = ( TraceInfo )itr.next();
				TraceNode top = ( TraceNode)stck.getLast();
				while ( top.traceInfo.end <= tinf.start ) {
					stck.removeLast();
					top = ( TraceNode )stck.getLast();
				}
				final TraceNode tnd = new TraceNode( tinf, top );
				top.children.add( tnd );
				stck.add( tnd );
    		}
    		dumpTraceNodes( rt, 0 );
    	}
	}

}
