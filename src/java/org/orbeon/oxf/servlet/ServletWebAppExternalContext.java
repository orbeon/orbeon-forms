package org.orbeon.oxf.servlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.util.AttributesToMap;

import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/*
 * Servlet-specific implementation of WebAppExternalContext.
 */
public class ServletWebAppExternalContext implements WebAppExternalContext {

    protected ServletContext servletContext;

    private Map initAttributesMap;
    private Map attributesMap;

    public ServletWebAppExternalContext(ServletContext servletContext) {
        this.servletContext = servletContext;

        Map result = new HashMap();
        for (Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            result.put(name, servletContext.getInitParameter(name));
        }
        this.initAttributesMap = Collections.unmodifiableMap(result);
    }

    public ServletWebAppExternalContext(ServletContext servletContext, Map initAttributesMap) {
        this.servletContext = servletContext;
        this.initAttributesMap = initAttributesMap;
    }

    public synchronized Map getInitAttributesMap() {
        return initAttributesMap;
    }

    public Map getAttributesMap() {
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
    public static class ServletContextMap extends AttributesToMap {
        public ServletContextMap(final ServletContext servletContext) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return servletContext.getAttribute(s);
                }

                public Enumeration getAttributeNames() {
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
