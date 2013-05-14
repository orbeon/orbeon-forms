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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.PortletContext;
import javax.portlet.PortletRequestDispatcher;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class PortletContextImpl implements PortletContext {

    private static final String SERVER_NAME = "Orbeon Forms Portlet Container";
    private static final int SERVER_MAJOR_VERSION = 1;
    private static final int SERVER_MINOR_VERSION = 0;

    private ExternalContext externalContext;
    private PortletConfigImpl portletConfig;

    public PortletContextImpl(ExternalContext externalContext, PortletConfigImpl portletConfig) {
        this.externalContext = externalContext;
        this.portletConfig = portletConfig;
    }

    public Object getAttribute(String name) {
        return externalContext.getAttributesMap().get(name);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(externalContext.getAttributesMap().keySet());
    }

    public String getInitParameter(String name) {
        return (String) externalContext.getInitAttributesMap().get(name);
    }

    public Enumeration getInitParameterNames() {
        return Collections.enumeration(externalContext.getInitAttributesMap().keySet());
    }

    public int getMajorVersion() {
        return SERVER_MAJOR_VERSION;
    }

    public String getMimeType(String file) {
        return null;//NIY / FIXME
//        return servletContext.getMimeType(file);
    }

    public int getMinorVersion() {
        return SERVER_MINOR_VERSION;
    }

    public PortletRequestDispatcher getNamedDispatcher(String name) {
        Object nativeContext = externalContext.getNativeContext();
        if (!(nativeContext instanceof ServletContext))
            throw new UnsupportedOperationException();
        RequestDispatcher requestDispatcher = ((ServletContext) nativeContext).getNamedDispatcher(name);
        return (requestDispatcher == null) ? null : new PortletRequestDispatcherImpl(portletConfig, requestDispatcher);
    }

    public String getPortletContextName() {
        return null;//NIY / FIXME
//        return null;//NIY / FIXME
    }

    public String getRealPath(String path) {
        return externalContext.getRealPath(path);
    }

    public PortletRequestDispatcher getRequestDispatcher(String path) {
        if (!path.startsWith("/"))
            throw new IllegalArgumentException("Path must start with a '/'");
        Object nativeContext = externalContext.getNativeContext();
        if (!(nativeContext instanceof ServletContext))
            throw new UnsupportedOperationException();
        RequestDispatcher requestDispatcher = ((ServletContext) nativeContext).getRequestDispatcher(path);
        return (requestDispatcher == null) ? null : new PortletRequestDispatcherImpl(portletConfig, requestDispatcher, path);
    }

    public URL getResource(String path) throws MalformedURLException {
        return null;//NIY / FIXME
//        return servletContext.getResource(path);
    }

    public InputStream getResourceAsStream(String path) {
        return null;//NIY / FIXME
//        return servletContext.getResourceAsStream(path);
    }

    public Set getResourcePaths(String path) {
        return null;//NIY / FIXME
//        return servletContext.getResourcePaths(path);
    }

    public String getServerInfo() {
        return SERVER_NAME;
    }

    public void log(String message, Throwable throwable) {
        externalContext.log(message, throwable);
    }

    public void log(String msg) {
        externalContext.log(msg);
    }

    public void removeAttribute(String name) {
        externalContext.getAttributesMap().remove(name);
    }

    public void setAttribute(String name, Object object) {
        externalContext.getAttributesMap().put(name, object);
    }
}
