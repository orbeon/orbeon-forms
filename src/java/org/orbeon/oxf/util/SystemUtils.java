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

    /**
     * @param pth	java.io.File.pathSeparator delimited path.
     * @return	What you think.
     */
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
    
    /**
     * @param	trgt	Receiver of path elements ( as java.io.File ) from pth.
     * @param	pth		java.io.File.pathSeparator delimited path.
     */
    public static void gatherPaths( final java.util.Collection trgt, final String pth ) {
    	final java.io.File[] files = pathToFiles( pth );
    	for ( int i = 0; i < files.length; i++ ) {
    		trgt.add( files[ i ] );
    	}
    }
    /**
     * @param c	Reciever of java.io.File's gathered from sun.boot.class.path elements
     * 			as well as the contents of the dirs named in java.ext.dirs.	
     */
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
    /**
     * Walks class loader hierarchy of clazz looking for instances of URLClassLoader.
     * For each found gets file urls and adds, converted, elements to the path.
     * Note that the more junior a class loader is the later in the path it's 
     * contribution is. <br/>
     * Also, tries to deal with fact that urls may or may not be encoded.
     * Converts the urls to java.io.File and checks for existence.  If it
     * exists file name is used as is.  If not name is URLDecoded.  If the
     * decoded form exists that that is used.  If neither exists then the
     * undecoded for is used. 
     * @param clazz	Class to try and build a classpath from.
     * @param enc character encoding to use
     * @return	java.io.File.pathSeparator delimited path.
     */
    public static String pathFromLoaders( final Class clazz, final String enc ) 
    throws java.io.UnsupportedEncodingException {
    	final java.util.TreeSet sysPths = new java.util.TreeSet();
    	gatherSystemPaths( sysPths );
    	final StringBuffer sbuf = new StringBuffer();
    	final java.util.LinkedList urlLst = new java.util.LinkedList();
    	for ( ClassLoader cl = clazz.getClassLoader(); cl != null; cl = cl.getParent() ) {
    		if ( !( cl instanceof java.net.URLClassLoader ) ) {
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
    	      itr.hasNext(); ) {
    		final java.net.URL url = ( java.net.URL )itr.next();
			final String fnam = url.getFile();
			final java.io.File fil = new java.io.File( fnam );
			if ( sysPths.contains( fil ) ) continue;
			// 11/14/2004 d : Odd test attempts to deal with fact that
			// a.) The URLs passed to a URLClassLoader don't have to be encoded
			//     in general.
			// b.) The app class loader and the extensions class loader, the
			//     jdk class loaders responsible for loading classes from classpath
			//     and the extensions dir respectively, do encode their urls.
			// c.) java.io.File.toURL doesn't produce encoded urls. ( Perhaps
			//     a sun bug... )
			// As you can see given (a) and (c) odds are pretty good that 
			// should anyone use something other than jdk class loaders we
			// will end up unencoded urls.
			if ( !fil.exists() ) {
				final String unEncNam = java.net.URLDecoder.decode( fnam, enc );
				final java.io.File unEncFil = new java.io.File( unEncNam );
				if ( unEncFil.exists() ) sbuf.append( unEncNam );
			} else {
				sbuf.append( fnam );
			}
			sbuf.append( java.io.File.pathSeparatorChar );
    	}
    	final String ret = sbuf.toString();
    	return ret;
    }
    
    private SystemUtils() {
    	// disallow instantiation
    }
}
