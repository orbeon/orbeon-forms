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
package org.orbeon.oxf.resources;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;

import java.util.jar.JarEntry;


/**
 * The classloader resource manager is able to load resources from a JAR file,
 * or from a directory in the system classpath. It is useful when resources are
 * bundled with an application.
 */
public class ClassLoaderResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(ClassLoaderResourceManagerImpl.class);

    private final Class clazz;
    private final boolean prependSlash;
    

    private java.net.URLConnection getConnection(final String key, boolean doNotThrowResourceNotFound) throws java.io.IOException {
        final String adjustedKey = prependSlash && !key.startsWith( "/" ) ? "/" + key : key;
        final java.net.URL u = clazz.getResource( adjustedKey );
        if (u == null) {
            if (doNotThrowResourceNotFound) return null;
            else throw new ResourceNotFoundException(key);
        }
        return u.openConnection();
    }

    /**
     * Initialize this resource manager.
     */
    public ClassLoaderResourceManagerImpl( final java.util.Map props ) {
        this( props, null );
    }

    /**
     * Initialize this resource manager with rooted in the specified class
     * @param c a root class
     */
    public ClassLoaderResourceManagerImpl( final java.util.Map props, final Class c ) {
        super( props );
        clazz = c == null ? getClass() : c;
        prependSlash = c == null;
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
    public java.io.InputStream getContentAsStream( String key ) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsStream(" + key + ")");

        final java.io.InputStream ret;
        try {
            final java.net.URLConnection uc = getConnection( key, false);
            uc.setUseCaches( false );
            ret = uc.getInputStream();
        } catch ( final java.io.IOException e ) {
            throw new OXFException( e );
        }
        return ret;
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    protected long lastModifiedImpl(final String key, boolean doNotThrowResourceNotFound) {
        /*
         * Slower implentations are commented out below.  Left them in place so we don't
         * re-implentment them...
         * 
         * Old impl was generating 303K of garbage per request to / in the examples app.
         *  
         */
        final long ret;
        done : try {
            final java.net.URLConnection uc = getConnection( key, doNotThrowResourceNotFound);
            if (uc == null) {
                ret = -1;
                break done;
            }
            if ( uc instanceof java.net.JarURLConnection ) {
                final JarEntry je 
                    = ( ( java.net.JarURLConnection )uc ).getJarEntry();
                ret = je.getTime();
                break done;
            } 
            final java.net.URL url = uc.getURL();
            final String prot = url.getProtocol();
            if ( "file".equalsIgnoreCase( prot ) ) {
                final String fnam = url.getPath();
                final java.io.File f = new java.io.File( fnam );
                if ( f.exists() ) {
                    ret = f.lastModified();
                } else {
                    final String fnamDec = java.net.URLDecoder.decode( fnam, "utf-8" );
                    final java.io.File fdec = new java.io.File( fnamDec );
                    ret = f.lastModified();
                }
            } else {
                final long l = NetUtils.getLastModified( uc );
                ret = l == 0 ? 1 : l;
            }
        } catch ( final java.io.IOException e ) {
            throw new OXFException( e );
        }
        return ret;
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        try {
            return getConnection( key, false).getContentLength();
        } catch (java.io.IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
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
