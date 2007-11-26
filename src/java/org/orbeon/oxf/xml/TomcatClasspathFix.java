/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xml;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.orbeon.oxf.pipeline.InitUtils;

public class TomcatClasspathFix {
    
    /**
     * This is just used by static block below.
     */
    private static void processManifest( final Manifest mf, final String base )
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final java.util.jar.Attributes attrs = mf.getMainAttributes();
        final String cp = attrs.getValue( "Class-Path" );
        if ( cp != null ) {
            final ClassLoader ldr = InitUtils.class.getClassLoader();
            final Class ldrCls = ldr.getClass();
            final Method mthd = ldrCls.getMethod( "addRepository", new Class[] { String.class } );
            for ( java.util.StringTokenizer st = new java.util.StringTokenizer( cp );
                  st.hasMoreTokens(); ) {
                final String tkn = st.nextToken();
                // Only care about cp entries that have a path cause tomcat will have
                // already added the ones without a path.
                if ( tkn.indexOf( '/' ) == -1 ) continue;
                final String cpEnt = base + tkn + "!/";
                mthd.invoke( ldr, new Object[] { cpEnt } );
            }
        }
    }
    
    private static void silentClose( final JarFile jar ) {
        if ( jar != null ) {
            try {
                jar.close();
            } catch ( final java.io.IOException e ) {
                // noop
            }
        }
    }
    
    
    
    /*
     * Work around broken Tomcat loader if need be
     * According to servlet spec and j2ee spec the container should honor
     * Class-Path entry in our jar's manifest.mf.   However Tomcat doesn't 
     * follow the spec and so we have to work around it.  ( A note on versions:
     * version 2.3, which Tomcat 4 supports, has stipulation so this pbm has
     * been around for some time now. )
     *
     * FWIW, we need this to work because we want META-INF/services in
     * orbeon.jar to be found before META-INF/services in xerces.*Impl.*.jar.
     *
     * And we want that to happen because we want to specify our own parser
     * config.  ( As opposed to the re-rooted one ).  The diff being that
     * the pres serv config sets an xincludehandler that lets us provide
     * caching support for xincluded files.
     */
    static {
        try {
            final ClassLoader ldr = InitUtils.class.getClassLoader();
            boolean isTCLdr = false;
            for ( Class ldrCls = ldr.getClass(); 
                  ldrCls != null && !isTCLdr; 
                  ldrCls = ldrCls.getSuperclass() ) {
                final String ldrClsName = ldrCls.getName();
                isTCLdr = "org.apache.catalina.loader.WebappClassLoader".equals( ldrClsName );
            }
            if ( isTCLdr ) {
                final java.net.URL url = InitUtils.class.getResource
                        ( "/org/orbeon/oxf/pipeline/InitUtils.class" );
                final String proto = url.getProtocol();
                done : if ( "jar".equalsIgnoreCase( proto ) ) {
                    final java.net.URLConnection uc = url.openConnection();
                    final java.net.JarURLConnection juc = ( java.net.JarURLConnection )uc;
                    final java.util.jar.Manifest mf = juc.getManifest();
                    if ( mf == null ) break done;
                    final String surl = url.toString();
                    int idxSep = surl.lastIndexOf( "!/" );
                    final int idxEnd = surl.lastIndexOf( '/', idxSep );
                    final String base = surl.substring( 0, idxEnd + 1 );
                    processManifest( mf, base );
                } else if ( "file".equalsIgnoreCase( proto ) ) {
                    // Assume InitUtils was found in WEB-INF/classes and that in WEB-INF/lib
                    // there is a jar ( e.g. orbeon.jar ) that will have a manifest we
                    // care about.  ( So yes in this case we process many more manifests
                    // than in the above case.
                    final String sPth = url.getPath().replaceAll("%20", " ");
                    final String sPthLC = sPth.toLowerCase();
                    final String webInf = "/web-inf/";
                    final int webInfIdx = sPthLC.lastIndexOf( webInf );
                    if ( webInfIdx == -1 ) break done;
                    final int end = webInfIdx + webInf.length();
                    final String base = sPth.substring( 0, end ) + "lib/";
                    final java.io.File libDir = new java.io.File( base );
                    final java.io.File[] files = libDir.listFiles();
                    final String baseURL = "jar:file:" + base;
                    for ( int i = 0; i < files.length; i++ ) {
                        final java.io.File file = files[ i ];
                        final String fnam = file.getName();
                        if ( !fnam.endsWith( ".jar" ) ) continue;
                        JarFile jar = null;
                        try {
                            jar = new JarFile( file );
                            final Manifest mf = jar.getManifest();
                            if ( mf == null ) continue;
                            processManifest( mf, baseURL );
                        } finally {
                            silentClose( jar );
                        }
                    }
                }          
            }
        } catch ( final Throwable t ) {
            throw new ExceptionInInitializerError( t ); 
        }
    }
    public static void applyIfNeedBe() {
        // Noop.  Actual work is done in <clinit>
    }
    private TomcatClasspathFix() {
        // disallow contruction   
    }
}
