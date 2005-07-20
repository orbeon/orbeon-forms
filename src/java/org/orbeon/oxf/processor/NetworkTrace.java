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

public class NetworkTrace implements Trace {
	
	private static final String host;
	private static final int port;
	
	static {
		final String hst = System.getProperty( "processor.trace.host" );
		host = hst == null ? "localhost" : hst;
		final String sprt = System.getProperty( "processor.trace.port" );
		port = sprt == null ? 9191 : Integer.parseInt( sprt );
	}

	final java.util.ArrayList traceInfs = new java.util.ArrayList( 0 );

	public void add( final TraceInfo tinf ) {
        traceInfs.add( tinf );
	}

	public void contextDestroyed( final boolean scss) {
		final java.util.Iterator itr = traceInfs.iterator();
		if ( itr.hasNext() ) {
			java.io.ObjectOutputStream oos = null;
			Throwable t = null;
			
			try {
				final java.net.Socket sckt = new java.net.Socket( host, port );
				final java.io.OutputStream os = sckt.getOutputStream();
				oos = new java.io.ObjectOutputStream( os );
                                oos.writeShort( 2501 ); // magic
                                oos.writeShort( 1 ); // version
				while ( itr.hasNext() ) {
					final TraceInfo tinf = ( TraceInfo )itr.next();
					oos.writeObject( tinf.systemID );
					oos.writeInt( tinf.line );
					oos.writeLong( tinf.start );
					oos.writeLong( tinf.end );
					
				}
			} catch ( final java.net.UnknownHostException e ) {
				t = e;
			} catch ( final java.io.IOException e ) {
				t = e;
			}
			finally {
				if ( oos != null ) {
					try {
						oos.close();
					} catch ( final java.io.IOException e ) {
						// noop
					}
				}
			}
			if ( t != null ) throw new OXFException( t );
		}
	}

}
