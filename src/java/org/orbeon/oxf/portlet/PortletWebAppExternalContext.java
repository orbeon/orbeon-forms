package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.util.AttributesToMap;

import javax.portlet.PortletContext;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/*
 * Portlet-specific implementation of WebAppExternalContext.
 */
public class PortletWebAppExternalContext implements WebAppExternalContext {

    protected PortletContext portletContext;
    private Map initAttributesMap;
    private Map attributesMap;

    public PortletWebAppExternalContext(PortletContext portletContext) {
        this.portletContext = portletContext;

        Map result = new HashMap();
        for (Enumeration e = portletContext.getInitParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            result.put(name, portletContext.getInitParameter(name));
        }
        this.initAttributesMap = Collections.unmodifiableMap(result);
    }

    public PortletWebAppExternalContext(PortletContext portletContext, Map initAttributesMap) {
        this.portletContext = portletContext;
        this.initAttributesMap = initAttributesMap;
    }

    public synchronized Map getInitAttributesMap() {
        return initAttributesMap;
    }

    public Map getAttributesMap() {
        if (attributesMap == null) {
            attributesMap = new PortletExternalContext.PortletContextMap(portletContext);
        }
        return attributesMap;
    }

    public String getRealPath(String path) {
        return portletContext.getRealPath(path);
    }

    public void log(String message, Throwable throwable) {
        portletContext.log(message, throwable);
    }

    public void log(String msg) {
        portletContext.log(msg);
    }

    public Object getNativeContext() {
        return portletContext;
    }

    /**
     * Present a view of the ServletContext properties as a Map.
     */
    public static class PortletContextMap extends AttributesToMap {
        public PortletContextMap(final PortletContext portletContext) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return portletContext.getAttribute(s);
                }

                public Enumeration getAttributeNames() {
                    return portletContext.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    portletContext.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    portletContext.setAttribute(s, o);
                }
            });
        }
    }
}
