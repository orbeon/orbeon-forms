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
package org.orbeon.oxf.webapp;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.WebAppResourceManagerImpl;
import org.orbeon.oxf.util.LoggerFactory;

import javax.servlet.ServletContext;
import java.util.*;
import java.lang.reflect.InvocationTargetException; 
import java.lang.reflect.Method;
import java.util.jar.JarFile;   
import java.util.jar.Manifest;


/**
 * WebAppContext is a singleton that represents context information for OXF
 * unique to an entire Web application.
 *
 * When the instance is created:
 *
 * 1. Initialize a resource manager
 * 2. Initialize Presentation Server Properties
 * 3. Initialize logger based on properties
 * 4. Initialize the processor registry
 */
public class WebAppContext {

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
    /*
     * Work around broken Tomcat loader if need be
     * According to servlet spec and j2ee spec the container should honor 
     * Class-Path entry in our jar's manifest.mf.   However Tomcat doesn't do 
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
            final Class ldrCls = ldr.getClass(); 
            final String ldrClsName = ldrCls.getName();
            if ( "org.apache.catalina.loader.WebappClassLoader".equals( ldrClsName ) ) {
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
                    final String sPth = url.getPath();
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
                        final JarFile jar = new JarFile( file );
                        final Manifest mf = jar.getManifest();
                        if ( mf == null ) continue;
                        processManifest( mf, baseURL );
                    }
                }
            }
        }
        catch ( final Throwable t ) {
            // We cannot cound on any logging support at this stage of execution
            //  so just resort to dumping this to std error.
            t.printStackTrace();
        }
    }


    public static final String PROPERTIES_PROPERTY = "oxf.properties";

    private static WebAppContext instance;
    private static Logger logger = LoggerFactory.createLogger(WebAppContext.class);

    private ServletContext servletContext;
    private Map contextInitParameters;

    public static WebAppContext instance() {
        if (instance == null)
            throw new OXFException("Presentation Server WebAppContext not initialized. Make sure at least one servlet or context listener is initialized first.");
        return instance;
    }

    /**
     * Initialize the context. This method has to be called at least once before Presentation
     * Server can be used.
     */
    public static synchronized WebAppContext instance(ServletContext servletContext) {
        if (instance == null)
            instance = new WebAppContext(servletContext);
        return instance;
    }

    private WebAppContext(ServletContext servletContext) {
        try {
            LoggerFactory.initBasicLogger();
            logger.info("Starting Presentation Server Release " + Version.getVersion());

            // Remember Servlet context
            this.servletContext = servletContext;

            // 1. Initialize the Resource Manager
            Map properties = new HashMap();
            for (Iterator i = getServletInitParametersMap().keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                if (name.startsWith("oxf.resources."))
                    properties.put(name, getServletInitParametersMap().get(name));
            }
            properties.put(WebAppResourceManagerImpl.SERVLET_CONTEXT_KEY, servletContext);
            logger.info("Initializing Resource Manager with: " + properties);

            ResourceManagerWrapper.init(properties);

            // 2. Initialize properties
            String propertiesFile = (String) getServletInitParametersMap().get(PROPERTIES_PROPERTY);
            if (propertiesFile != null)
                OXFProperties.init(propertiesFile);

            // 3. Initialize log4j with a DOMConfiguration
            LoggerFactory.initLogger();

            // 4. Register processor definitions with the default XML Processor Registry
            InitUtils.initializeProcessorDefinitions();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Return an unmodifiable Map of the Servlet initialization parameters.
     */
    public Map getServletInitParametersMap() {
        if (contextInitParameters == null) {
            synchronized (this) {
                if (contextInitParameters == null) {
                    Map result = new HashMap();
                    for (Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
                        String name = (String) e.nextElement();
                        result.put(name, servletContext.getInitParameter(name));
                    }
                    contextInitParameters = Collections.unmodifiableMap(result);
                }
            }
        }
        return contextInitParameters;
    }
}
