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
package org.orbeon.oxf.resources;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;


/**
 * The classloader resource manager is able to load resources from a JAR file,
 * or from a directory in the system classpath. It is useful when resources are
 * bundled with an application.
 */
public class ClassLoaderResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(ClassLoaderResourceManagerImpl.class);

    private Class clazz;
    
    private java.net.URL getResource( final String key ) {
        return (clazz == null ? getClass() : clazz).getResource(((clazz == null && !key.startsWith("/")) ? "/" : "") + key);
    }

    /**
     * Initialize this resource manager.
     */
    public ClassLoaderResourceManagerImpl(java.util.Map props) {
        super(props);
    }

    /**
     * Initialize this resource manager with rooted in the specified class
     * @param clazz a root class
     */
    public ClassLoaderResourceManagerImpl(java.util.Map props, Class clazz) {
        super(props);
        this.clazz = clazz;
    }

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
    public java.io.Reader getContentAsReader(String key) {
        return new java.io.InputStreamReader(getContentAsStream(key));
    }

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public java.io.InputStream getContentAsStream(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsStream(" + key + ")");

        java.io.InputStream result = (clazz == null ? getClass() : clazz).getResourceAsStream
                ((clazz == null && !key.startsWith("/") ? "/" : "") + key);
        if (result == null)
            throw new ResourceNotFoundException("Cannot load \"" + key + "\" with class loader");
        return result;
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @return a timestamp
     */
    protected long lastModifiedImpl(String key) {
        /*
         * Opening JarURLConnections is expensive.  When possible just use the time stamp of the jar 
         * file.
         * Wrt expensive, delta in heap dump info below is amount of bytes allocated during the 
         * handling of a single request to '/' in the examples app. i.e. The trace below was 
         * responsible for creating 300k of garbage during the handing of a single request to '/'.
         * 
         * delta: 303696 live: 1017792 alloc: 1264032 trace: 381205 class: byte[]
         * 
         * TRACE 381205:
         * java.io.BufferedInputStream.<init>(BufferedInputStream.java:178)
         * java.io.BufferedInputStream.<init>(BufferedInputStream.java:158)
         * sun.net.www.protocol.file.FileURLConnection.connect(FileURLConnection.java:70)
         * sun.net.www.protocol.file.FileURLConnection.initializeHeaders(FileURLConnection.java:90)
         * sun.net.www.protocol.file.FileURLConnection.getHeaderField(FileURLConnection.java:126)
         * sun.net.www.protocol.jar.JarURLConnection.getHeaderField(JarURLConnection.java:178)
         * java.net.URLConnection.getHeaderFieldDate(URLConnection.java:597)
         * java.net.URLConnection.getLastModified(URLConnection.java:526)
         * org.orbeon.oxf.util.NetUtils.getLastModified(NetUtils.java:119)
         * org.orbeon.oxf.resources.ClassLoaderResourceManagerImpl.lastModifiedImpl(ClassLoaderResourceManagerImpl.java:89)
         * org.orbeon.oxf.resources.ResourceManagerBase.lastModified(ResourceManagerBase.java:299)
         * org.orbeon.oxf.resources.PriorityResourceManagerImpl$5.run(PriorityResourceManagerImpl.java:125)
         * org.orbeon.oxf.resources.PriorityResourceManagerImpl.delegate(PriorityResourceManagerImpl.java:232)
         * org.orbeon.oxf.resources.PriorityResourceManagerImpl.lastModified(PriorityResourceManagerImpl.java:123)
         * org.orbeon.oxf.processor.generator.URLGenerator$OXFResourceHandler.getValidity(URLGenerator.java:553)
         * org.orbeon.oxf.processor.generator.URLGenerator$1.getHandlerValidity(URLGenerator.java:470)
         * 
         * This mod brings gc time/app time ratio down from 1.778586943371413 to 1.5751878434169833.
         * ( 1500 samples, 50 threads, 512M, jdk 1.4.2.06, TC 4.1.30, ops 2.7.2 )
         */
        try {
            java.net.URL resource = getResource( key );
            if (resource == null)
                throw new ResourceNotFoundException("Cannot read from file " + key);

            final String pth;
            final String jrPth = SystemUtils.getJarFilePath( resource );
            if ( jrPth == null ) {
                final String prot = resource.getProtocol();
                if ( "file".equalsIgnoreCase( prot ) ) {
                    pth = resource.getPath();
                } else {
                    pth = null;
                }
            } else {
                pth = jrPth;
            }

            long lm;
            if ( pth == null ) {
                lm = NetUtils.getLastModified(resource.openConnection());
            } else {
                final java.io.File f = new java.io.File( pth );
                lm = f.exists() ? f.lastModified() : 0;
            }

            // If the class loader cannot determine this, we assume that the
            // file will not change.
            if (lm == 0) lm = 1;
            return lm;
        } catch (java.io.IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        try {
            return (clazz == null ? getClass() : clazz).getResource
                    (((clazz == null && !key.startsWith("/")) ? "/" : "") + key).openConnection().getContentLength();
        } catch (java.io.IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite() {
        return false;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public java.io.OutputStream getOutputStream(String key) {
        throw new OXFException("Write Operation not supported");
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public java.io.Writer getWriter(String key) {
        throw new OXFException("Write Operation not supported");
    }

    public String getRealPath(String key) {
        return null;
    }

}
