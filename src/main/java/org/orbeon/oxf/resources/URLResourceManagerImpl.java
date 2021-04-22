/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/**
 * The URL resource manager is able to load ressources from any
 * URL supported by the JVM.
 */
public class URLResourceManagerImpl extends ResourceManagerBase {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(URLResourceManagerImpl.class);

    protected URL baseURL;

    public URLResourceManagerImpl(Map props) throws OXFException {
        super(props);
        String root = (String) props.get(URLResourceManagerFactory.BASE_URL);
        if (root == null)
            throw new OXFException("Property " + URLResourceManagerFactory.BASE_URL + " must be set.");
        // Clean-up URL
        root = StringUtils.trimAllToEmpty(root);
        if (!root.endsWith("/"))
            root = root + "/";

        try {
            baseURL = new URL(root);
        } catch (MalformedURLException e) {
            throw new OXFException(e);
        }
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
        URL url = getURL(key);
        try {
            return url.openStream();
        } catch (IOException ioe) {
            throw new ResourceNotFoundException(key);// NOTE: could also pass resolved URL in addition
        }
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    public long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        URL url = getURL(key);
        try {
            return NetUtils.getLastModified(url);
        } catch (IOException e) {
            if (doNotThrowResourceNotFound) return -1;
            else throw new ResourceNotFoundException(key);// NOTE: could also pass resolved URL in addition
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        if (logger.isDebugEnabled())
            logger.debug("length(" + key + ")");
        final URL url = getURL(key);
        try {
            final URLConnection conn = url.openConnection();
            if (conn instanceof HttpURLConnection)
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
            try {
                return conn.getContentLength();
            } finally {
                conn.getInputStream().close();
            }
        } catch (IOException e) {
            throw new ResourceNotFoundException(key);// NOTE: could also pass resolved URL in addition
        }
    }

    public String getRealPath(String key) {
        return null;
    }

    private URL getURL(String key) {
        try {
            if (key.startsWith("/"))
                return new URL(baseURL, key.substring(1));
            else
                return new URL(baseURL, key);
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException(key);
        }
    }
}
