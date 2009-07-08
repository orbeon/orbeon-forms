package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.util.AttributesToMap;

import javax.portlet.PortletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/*
 * Portlet-specific implementation of WebAppExternalContext.
 */
public class PortletWebAppExternalContext implements WebAppExternalContext {

    protected PortletContext portletContext;
    private Map<String, String> initAttributesMap;
    private Map<String, Object> attributesMap;

    public PortletWebAppExternalContext(PortletContext portletContext) {
        this.portletContext = portletContext;

        final Map<String, String> result = new HashMap<String, String>();
        for (Enumeration e = portletContext.getInitParameterNames(); e.hasMoreElements();) {
            final String name = (String) e.nextElement();
            result.put(name, portletContext.getInitParameter(name));
        }
        this.initAttributesMap = Collections.unmodifiableMap(result);
    }

    public PortletWebAppExternalContext(PortletContext portletContext, Map<String, String> initAttributesMap) {
        this.portletContext = portletContext;
        this.initAttributesMap = initAttributesMap;
    }

    public synchronized Map<String, String> getInitAttributesMap() {
        return initAttributesMap;
    }

    public Map<String, Object> getAttributesMap() {
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

    protected PortletContext getPortletContext() {
        return portletContext;
    }

    /**
     * Present a view of the ServletContext properties as a Map.
     */
    public static class PortletContextMap extends AttributesToMap<Object> {
        public PortletContextMap(final PortletContext portletContext) {
            super(new Attributeable<Object>() {
                public Object getAttribute(String s) {
                    return portletContext.getAttribute(s);
                }

                public Enumeration<String> getAttributeNames() {
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
