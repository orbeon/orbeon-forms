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
package org.orbeon.oxf.servlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.webapp.OXFClassLoader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * OXFServlet is the Servlet entry point of OXF.
 *
 * Several OXFServlet and OXFPortlet instances can be used in the same Web or Portlet application.
 * They all share the same Servlet context initialization parameters, but each Servlet can be
 * configured with its own main processor and inputs.
 *
 * All OXFServlet and OXFPortlet instances in a given Web application share the same resource
 * manager.
 *
 * WARNING: This class must only depend on the Servlet API and the OXF Class Loader.
 */
public class OXFServlet extends HttpServlet {

    // Servlet delegate
    private HttpServlet delegateServlet;

    // WebAppExternalContext
    private WebAppExternalContext webAppExternalContext;

    /**
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
    private static void workAroundBrokenTomcatLoaderIfNeedBe()
    {
        try {
            final ClassLoader ldr = OXFServlet.class.getClassLoader();
            final Class ldrCls = ldr.getClass(); 
            if ( "org.apache.catalina.loader.WebappClassLoader".equals( ldrCls.getName() ) ) {
                final Method mthd = ldrCls.getMethod( "addRepository", new Class[] { String.class } );
                final java.net.URL url = OXFServlet.class.getResource
                        ( "/org/orbeon/oxf/servlet/OXFServlet.class" );
                final java.net.URLConnection uc = url.openConnection();
                if ( uc instanceof java.net.JarURLConnection ) {
                    final java.net.JarURLConnection juc = ( java.net.JarURLConnection )uc;
                    final java.util.jar.Manifest mf = juc.getManifest();
                    final java.util.jar.Attributes attrs = mf.getMainAttributes();
                    final String cp = attrs.getValue( "Class-Path" );
                    final String surl = url.toString();
                    int idxSep = surl.lastIndexOf( "!/" );
                    final int idxEnd = surl.lastIndexOf( '/', idxSep );
                    final String base = surl.substring( 0, idxEnd + 1 );
                    for ( java.util.StringTokenizer st = new java.util.StringTokenizer( cp ); 
                          st.hasMoreTokens(); ) {
                        final String tkn = st.nextToken() + "!/";
                        if ( tkn.indexOf( '/' ) == -1 ) continue;
                        final String cpEnt = base + tkn;
                        mthd.invoke( ldr, new Object[] { cpEnt } );
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

    public void init() throws ServletException {
        workAroundBrokenTomcatLoaderIfNeedBe();
        try {
            // Instanciate WebAppExternalContext
            webAppExternalContext = new ServletWebAppExternalContext(getServletContext());

            // Instanciate Servlet delegate
            Class delegateServletClass = OXFClassLoader.getClassLoader(webAppExternalContext).loadClass(OXFServlet.class.getName() + OXFClassLoader.DELEGATE_CLASS_SUFFIX);
            delegateServlet = (HttpServlet) delegateServletClass.newInstance();

            // Initialize Servlet delegate
            Thread currentThread = Thread.currentThread();
            ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
                delegateServlet.init(getServletConfig());
            } finally {
                currentThread.setContextClassLoader(oldThreadContextClassLoader);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Delegate to Servlet delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            delegateServlet.service(request, response);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void destroy() {
        // Delegate to Servlet delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            delegateServlet.destroy();
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }
}
