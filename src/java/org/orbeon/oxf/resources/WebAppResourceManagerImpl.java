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

import javax.servlet.ServletContext;
import java.io.*;
import java.net.URL;
import java.util.Map;

/**
 * The webapp resource manager is able to load resources from a WAR file. This
 * is very useful when distributing packaged applications.
 */
public class WebAppResourceManagerImpl extends ResourceManagerBase {

    public static final String SERVLET_CONTEXT_KEY = WebAppResourceManagerImpl.class.getName() + "ServletContext";
    public static final String ROOT_DIR = "oxf.resources.webapp.rootdir";

    private static Logger logger = LoggerFactory.createLogger(WebAppResourceManagerImpl.class);

    private ServletContext servletContext;
    private String rootDirectory;

    public WebAppResourceManagerImpl(Map props) {
        super(props);
        ServletContext ctx = (ServletContext) props.get(SERVLET_CONTEXT_KEY);
        if (ctx == null)
            throw new OXFException("WebAppResourceManager needs a ServletContext object in its map (key=" + SERVLET_CONTEXT_KEY + ")");
        this.servletContext = ctx;

        String root = (String) props.get(ROOT_DIR);
        if (root == null)
            throw new OXFException("WebAppResourceManager: property " + ROOT_DIR + " is null");
        this.rootDirectory = root;
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

        InputStream result = servletContext.getResourceAsStream(rootDirectory + key);
        if (result == null)
            throw new ResourceNotFoundException(key);
        return result;
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    protected long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        try {
            long lm;
            String realPath = servletContext.getRealPath(rootDirectory + key);
            if (realPath == null) {
                // Some application server do not return a real path, as they do
                // not uncompress the WAR file. This is in particular the case
                // when deploying compressed WAR files on Tomcat.
                URL url = servletContext.getResource(rootDirectory + key);
                if (url == null) {
                    if (doNotThrowResourceNotFound) return -1;
                    else throw new ResourceNotFoundException(key);
                }
                lm = url.openConnection().getLastModified();
            } else {
                File file = new File(realPath);
                if (!file.canRead()) {
                    if (doNotThrowResourceNotFound) return -1;
                    else throw new ResourceNotFoundException(key);
                }
                lm = file.lastModified();
            }
            if (lm == 0) lm = 1;
            return lm;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Indicates if the resource manager implementation supports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
        return getRealPath(key) != null;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {

        final String realPath = getRealPath(key);
        if (realPath == null)
            throw new OXFException("Write Operation not supported");

        try {
            return new FileOutputStream(realPath);
        } catch (FileNotFoundException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        final String realPath = getRealPath(key);
        if (realPath == null)
            throw new OXFException("Write Operation not supported");

        try {
            return new FileWriter(realPath);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        String realPath = servletContext.getRealPath(rootDirectory + key);
        if (realPath == null) {
            // FIXME: this happens when the resources are in a WAR file in
            // WLS. In this case we should try to figure out the last modified of
            // the WAR itself.
            return 0;
        } else {
            return new Long(new File(realPath).length()).intValue();
        }
    }

    public String getRealPath(String key) {
        return servletContext.getRealPath(rootDirectory + key);
        // Need an option for this as some callers call this for non-existing files
//        final String realPath = servletContext.getRealPath(rootDirectory + key);
//        if (realPath == null)
//            return null;
//
//        if (!new File(realPath).canRead())
//            throw new ResourceNotFoundException("Cannot read from file " + key);
//        else
//            return realPath;
    }
}