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
package org.orbeon.oxf.servlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.util.AttributesToMap;

import javax.servlet.ServletContext;
import java.util.*;

/*
 * Servlet-specific implementation of WebAppExternalContext.
 */
public class ServletWebAppExternalContext implements WebAppExternalContext {

    protected ServletContext servletContext;

    private Map<String, String> initAttributesMap;
    private Map<String, Object> attributesMap;

    public ServletWebAppExternalContext(ServletContext servletContext) {
        this.servletContext = servletContext;

        final Map<String, String> result = new HashMap<String, String>();
        for (Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            result.put(name, servletContext.getInitParameter(name));
        }
        this.initAttributesMap = Collections.unmodifiableMap(result);
    }

    public ServletWebAppExternalContext(ServletContext servletContext, Map<String, String> initAttributesMap) {
        this.servletContext = servletContext;
        this.initAttributesMap = initAttributesMap;
    }

    public synchronized Map<String, String> getInitAttributesMap() {
        return initAttributesMap;
    }

    public Map<String, Object> getAttributesMap() {
        if (attributesMap == null) {
            attributesMap = new ServletContextMap(servletContext);
        }
        return attributesMap;
    }

    public String getRealPath(String path) {
        return servletContext.getRealPath(path);
    }

    public void log(String message, Throwable throwable) {
        servletContext.log(message, throwable);
    }

    public void log(String msg) {
        servletContext.log(msg);
    }

    public Object getNativeContext() {
        return servletContext;
    }

    /**
     * Present a view of the ServletContext properties as a Map.
     */
    public static class ServletContextMap extends AttributesToMap<Object> {
        public ServletContextMap(final ServletContext servletContext) {
            super(new Attributeable<Object>() {
                public Object getAttribute(String s) {
                    return servletContext.getAttribute(s);
                }

                public Enumeration<String> getAttributeNames() {
                    return servletContext.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    servletContext.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    servletContext.setAttribute(s, o);
                }
            });
        }
    }
}
