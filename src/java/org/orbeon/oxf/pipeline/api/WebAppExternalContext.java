package org.orbeon.oxf.pipeline.api;

import java.util.Map;

/**
 * WebAppExternalContext abstracts context information so that compile-time dependencies on the
 * Servlet API or Portlet API can be removed.
 */
public interface WebAppExternalContext {

    public Map<String, Object> getAttributesMap();
    public Map<String, String> getInitAttributesMap();
    public String getRealPath(String path);

    public void log(String message, Throwable throwable);
    public void log(String msg);

    public Object getNativeContext();

}
