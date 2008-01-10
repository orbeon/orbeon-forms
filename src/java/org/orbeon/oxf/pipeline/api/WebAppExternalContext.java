package org.orbeon.oxf.pipeline.api;

import java.util.Map;

/**
 * WebAppExternalContext abstracts context information so that compile-time dependencies on the
 * Servlet API or Portlet API can be removed.
 */
public interface WebAppExternalContext {

    public Map getAttributesMap();
    public Map getInitAttributesMap();
    public String getRealPath(String path);

    public void log(String message, Throwable throwable);
    public void log(String msg);

    public Object getNativeContext();

    public interface Application {
        public void addListener(ApplicationListener applicationListener);
        public void removeListener(ApplicationListener applicationListener);

        public interface ApplicationListener {
            public void servletDestroyed();
        }
    }

}
