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

import java.io.*;
import java.net.URL;
import java.util.Map;

/**
 * The classloader resource manager is able to load resources from a JAR file,
 * or from a directory in the system classpath. It is useful when resources are
 * bundled with an application.
 */
public class ClassLoaderResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(ClassLoaderResourceManagerImpl.class);

    private Class clazz;

    /**
     * Initialize this resource manager.
     */
    public ClassLoaderResourceManagerImpl(Map props) {
        super(props);
    }

    /**
     * Initialize this resource manager with rooted in the specified class
     * @param clazz a root class
     */
    public ClassLoaderResourceManagerImpl(Map props, Class clazz) {
        super(props);
        this.clazz = clazz;
    }

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
    public Reader getContentAsReader(String key) {
        return new InputStreamReader(getContentAsStream(key));
    }

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsStream(" + key + ")");

        InputStream result = (clazz == null ? getClass() : clazz).getResourceAsStream
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
        try {
            URL resource = (clazz == null ? getClass() : clazz).getResource(((clazz == null && !key.startsWith("/")) ? "/" : "") + key);
            if (resource == null)
                throw new ResourceNotFoundException("Cannot read from file " + key);
            long lm = NetUtils.getLastModified(resource.openConnection());
            // If the class loader cannot determine this, we assume that the
            // file will not change.
            if (lm == 0) lm = 1;
            return lm;
        } catch (IOException e) {
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
        } catch (IOException e) {
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
    public OutputStream getOutputStream(String key) {
        throw new OXFException("Write Operation not supported");
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        throw new OXFException("Write Operation not supported");
    }

    public String getRealPath(String key) {
        return null;
    }

}
