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

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpURL;
import org.apache.commons.httpclient.URIException;
import org.apache.webdav.lib.WebdavResource;
import org.orbeon.oxf.common.OXFException;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;

/**
 * Simple WebDAV resource manager implementation.
 */
public class WebDAVResourceManagerImpl extends ResourceManagerBase {

    private HttpURL baseURL;
    private String username;
    private String password;

    public WebDAVResourceManagerImpl(Map properties) {
        super(properties);
        // Read properties
        String baseURLString = getProperty(properties, WebDAVResourceManagerFactory.BASE_URL);
        if (baseURLString == null)
            throw new OXFException("Property " + WebDAVResourceManagerFactory.BASE_URL + " must be set.");

        username = getProperty(properties, WebDAVResourceManagerFactory.USERNAME);
        password = getProperty(properties, WebDAVResourceManagerFactory.PASSWORD);

        if (username == null && password != null)
            throw new OXFException("Property " + WebDAVResourceManagerFactory.USERNAME + " must be set.");
        if (username != null && password == null)
            throw new OXFException("Property " + WebDAVResourceManagerFactory.PASSWORD + " must be set.");

        // Clean-up URL
        baseURLString = baseURLString.trim();
        if (!baseURLString.endsWith("/"))
            baseURLString = baseURLString + "/";

        // Create HttpURL
        try {
            baseURL = new HttpURL(baseURLString);
        } catch (URIException e) {
            throw new OXFException(e);
        }
    }

    protected long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        HttpURL httpURL = getURL(key);
        try {
            WebdavResource webdavResource = new WebdavResource(httpURL);
            return webdavResource.getGetLastModified();
        } catch (HttpException e) {
            if (e.getReasonCode() == HttpStatus.SC_NOT_FOUND) {
                if (doNotThrowResourceNotFound) return -1;
                else throw new ResourceNotFoundException("Cannot connect to URL: " + httpURL);
            } else {
                throw new OXFException(e);
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public int length(String key) {
        HttpURL httpURL = getURL(key);
        try {
            WebdavResource webdavResource = new WebdavResource(httpURL);
            return (int) webdavResource.getGetContentLength();
        } catch (HttpException e) {
            if (e.getReasonCode() == HttpStatus.SC_NOT_FOUND)
                throw new ResourceNotFoundException("Cannot connect to URL: " + httpURL);
            else
                throw new OXFException(e);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public boolean canWrite(String key) {
        return false;
    }

    public InputStream getContentAsStream(String key) {
        HttpURL httpURL = getURL(key);
        try {
            WebdavResource webdavResource = new WebdavResource(httpURL);
            return webdavResource.getMethodData();
        } catch (HttpException e) {
            if (e.getReasonCode() == HttpStatus.SC_NOT_FOUND)
                throw new ResourceNotFoundException("Cannot connect to URL: " + httpURL);
            else
                throw new OXFException(e);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public OutputStream getOutputStream(String key) {
        throw new OXFException("Write Operation not supported");
    }

    public String getRealPath(String key) {
        throw new OXFException("Getting real path not supported");
    }

    public Writer getWriter(String key) {
        throw new OXFException("Write Operation not supported");
    }

    private HttpURL getURL(String key) {
        try {
            HttpURL httpURL =  (key.startsWith("/")) ? new HttpURL(baseURL, key.substring(1)) : new HttpURL(baseURL, key);
            if (username != null && password != null)
                httpURL.setUserinfo(username, password);
            return httpURL;
        } catch (URIException e) {
            throw new ResourceNotFoundException("Cannot build URL from key: " + key);
        }
    }

    private String getProperty(Map properties, String name) {
        String value = (String) properties.get(name);
        value = value == null ? null : value.trim();
        if ("".equals(value))
            value = null;
        return value;
    }
}
