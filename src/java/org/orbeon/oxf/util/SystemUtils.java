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
package org.orbeon.oxf.util;


import java.io.File;

public class SystemUtils {

    public static File getTemporaryDirectory() {
        return new File(System.getProperty("java.io.tmpdir")).getAbsoluteFile();
    }

    public static void gatherPaths( final java.util.Collection trgt, final String pth ) {
    	final java.io.File[] files = pathToFiles( pth );
    	for ( int i = 0; i < files.length; i++ ) {
    		trgt.add( files[ i ] );
    	}
    }
    
    public static java.io.File[] pathToFiles( final String pth ) {
    	final java.util.ArrayList pthArr = new java.util.ArrayList();
    	final java.util.StringTokenizer st = new java.util.StringTokenizer
		    ( pth, java.io.File.pathSeparator );
    	while ( st.hasMoreTokens() ) {
    		final String fnam = st.nextToken();
    		final java.io.File fil = new java.io.File( fnam );
    		pthArr.add( fil );
    	}
    	final java.io.File[] ret = new java.io.File[ pthArr.size() ];
    	pthArr.toArray( ret );
    	return ret;
    }
    
    public static void gatherSystemPaths( java.util.Collection c ) {
    	final String bootcp = System.getProperty( "sun.boot.class.path" );
    	gatherPaths( c, bootcp );
    	final String extDirProp = System.getProperty( "java.ext.dirs" );
    	final java.io.File[] extDirs = pathToFiles( extDirProp );
    	for ( int i = 0; i < extDirs.length; i++ ) {
    		final java.io.File[] jars = extDirs[ i ].listFiles();
    		for ( int j = 0; j < jars.length; j++ ) {
    			final java.io.File fil = jars[ j ];
    			final String fnam = fil.toString();
    			final int fnamLen = fnam.length();
    			if ( fnamLen < 5 ) continue;
    			if ( fnam.regionMatches( true, fnamLen - 4, ".jar", 0, 4 ) ) {
    				c.add( fil );
    			}
    			else if ( fnam.regionMatches( true, fnamLen - 4, ".zip", 0, 4 ) ) {
    				c.add( fil );
    			} 
    		}
    	}
    }
    
    public static String pathFromLoaders( final Class clazz ) {
    	final java.util.TreeSet sysPths = new java.util.TreeSet();
    	gatherSystemPaths( sysPths );
    	final StringBuffer sbuf = new StringBuffer();
    	final java.util.LinkedList urlLst = new java.util.LinkedList();
    	for ( ClassLoader cl = clazz.getClassLoader(); cl != null; cl = cl.getParent() ) {
    		if ( !( cl instanceof java.net.URLClassLoader ) ) 
    		{
    			continue;
    		}
    		final java.net.URLClassLoader ucl = ( java.net.URLClassLoader )cl;
    		final java.net.URL[] urls = ucl.getURLs();
    		if ( urls == null ) continue;
    		for ( int i = urls.length - 1; i > -1; i-- ) {
    			final java.net.URL url = urls[ i ];
    			final String prot = url.getProtocol();
    			if ( !"file".equalsIgnoreCase( prot ) ) continue;
    			urlLst.addFirst( url );
    		}
    	}
    	for ( final java.util.Iterator itr = urlLst.iterator();
    	      itr.hasNext(); )
    	{
    		final java.net.URL url = ( java.net.URL )itr.next();
			final String fnam = url.getFile();
			final java.io.File fil = new java.io.File( fnam );
			if ( sysPths.contains( fil ) ) continue;
			sbuf.append( fnam );
			sbuf.append( java.io.File.pathSeparatorChar );
    	}
    	final String ret = sbuf.toString();
    	return ret;
    }
    
    private SystemUtils() {
    	// disallow instantiation
    }
}
