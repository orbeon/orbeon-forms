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

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.portlet.processor.PortletIncludeGenerator;

import javax.portlet.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/**
 * Naming convention followed:
 *
 * {urlPrefix}?x1.param1=containerValue1&x1.param2=containerValue2&p1.param1=userValue1&p1.param2=userValue2&...
 *
 * TODO: creating secure URLs is not supported yet
 */
public class PortletURLImpl implements PortletURL {


    public static final int ACTION_URL = 1;
    public static final int USER_URL = 2;

    private static final String PORTLET_MODE_NAME = "mode";
    private static final String WINDOW_STATE_NAME = "state";
    private static final String TARGET_NAME = "target";

    private static final String ACTION_VALUE = "a";
    private static final String RENDER_VALUE = "r";

    private static final String PARAM_PREFIX = "$portlet$";
    private static final char USER_PARAM_PREFIX_CHAR = 'p';
    private static final char ACTION_USER_PARAM_PREFIX_CHAR = 'a';
    private static final String USER_PARAM_PREFIX = "" + USER_PARAM_PREFIX_CHAR;
    private static final String ACTION_USER_PARAM_PREFIX = "" + ACTION_USER_PARAM_PREFIX_CHAR;
    private static final String CONTAINER_PARAM_PREFIX = "x";

    private PipelineContext portalPipelineContext;
    private ExternalContext portalExternalContext;
    private int portletId;
    private String urlPrefix;

    private Map parameters = new HashMap();
    private PortletMode portletMode;
    private WindowState windowState;
    private boolean isAction;


    PortletURLImpl(PipelineContext portalPipelineContext, ExternalContext portalExternalContext, int portletId, String urlPrefix, int urlType) {
        this.portalPipelineContext = portalPipelineContext;
        this.portalExternalContext = portalExternalContext;
        this.portletId = portletId;
        this.urlPrefix = urlPrefix;
        this.isAction = urlType == ACTION_URL;
    }

    public void setParameter(String name, String value) {
        parameters.put(name, new String[] { value });
    }

    public void setParameter(String name, String[] values) {
        parameters.put(name, values);
    }

    public void setParameters(Map parameters) {
        this.parameters = parameters;
    }

    public void setPortletMode(PortletMode portletMode) throws PortletModeException {
        // TODO: check that mode is allowed
        this.portletMode = portletMode;
    }

    public void setSecure(boolean secure) throws PortletSecurityException {
        //this.secure = secure;
        // NIY / FIXME
        if (secure)
            throw new PortletSecurityException("Secure mode is unsupported.");
    }

    public void setWindowState(WindowState windowState) throws WindowStateException {
        // TODO: check that window state is allowed
        this.windowState = windowState;
    }

    private void appendContainerParameter(boolean first, StringBuffer sb, String name, String value) {
        sb.append(first ? "?" : "&amp;");// CHECK: who escapes ampersand?
        sb.append(PARAM_PREFIX);
        sb.append(CONTAINER_PARAM_PREFIX);
        sb.append(portletId);
        sb.append('.');
        sb.append(name);
        sb.append('=');
        sb.append(value);
    }

    private void appendUserParameter(boolean first, StringBuffer sb, String name, String value) {
        sb.append(first ? "?" : "&amp;");// CHECK: who escapes ampersand?
        sb.append(PARAM_PREFIX);
        sb.append(USER_PARAM_PREFIX);
        sb.append(portletId);
        sb.append('.');
        sb.append(name);
        sb.append('=');
        sb.append(value);
    }

    private void appendRawParameter(boolean first, StringBuffer sb, String name, String value) {
        sb.append(first ? "?" : "&amp;");// CHECK: who escapes ampersand?
        sb.append(name);
        sb.append('=');
        sb.append(value);
    }

    public String toString() {
        // Start with prefix
        StringBuffer sb = new StringBuffer(urlPrefix);

        // Append other portlets parameters
        // NIY

        // Append container parameters
//        appendContainerParameter(first, sb, PORTLET_ID_NAME, Integer.toString(portletId));

        appendContainerParameter(true, sb, TARGET_NAME, isAction ? ACTION_VALUE : RENDER_VALUE);

        if (portletMode != null)
            appendContainerParameter(false, sb, PORTLET_MODE_NAME, portletMode.toString());
        if (windowState != null)
            appendContainerParameter(false, sb, WINDOW_STATE_NAME, windowState.toString());

        //URLEncoder.encode(s, "UTF-8");

        // Append user parameters
        for (Iterator i = parameters.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            String[] value = (String[]) parameters.get(name);
            for (int j = 0; j < value.length; j++) {
                appendUserParameter(false, sb, name, value[j]);
            }
        }

        // Append other container parameters
        final Document portalXFormsInstanceDocument = (Document) portalExternalContext.getRequest().getAttributesMap().get(PortletIncludeGenerator.REQUEST_PORTAL_INSTANCE_DOCUMENT);
        if (portalXFormsInstanceDocument != null) {
            final String instanceString = XFormsUtils.encodeXML(portalPipelineContext, portalXFormsInstanceDocument, false);
            try {
                appendRawParameter(false, sb, "$instance", URLEncoder.encode(instanceString, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                // Should not happen
                throw new OXFException(e);
            }
        }

        return sb.toString();
    }

    /**
     * Return a Map, indexed by portlet id, of parameter Maps. A parameter Map
     * is indexed by parameter name and contains values of type String[];
     */
    public static RequestParameters extractParameters(Map parameterMap) {
        RequestParameters requestParameters = new RequestParameters();

        Map extraParameters = null;
        for (Iterator i = parameterMap.keySet().iterator(); i.hasNext();) {
            try {
                String encodedName = (String) i.next();
                // Extract actual name and determine if it is a container or user parameter
                if (encodedName.startsWith(PARAM_PREFIX)) {
                    String type = encodedName.substring(PARAM_PREFIX.length(), PARAM_PREFIX.length() + USER_PARAM_PREFIX.length());
                    boolean isUser = type.equals(USER_PARAM_PREFIX) || type.equals(ACTION_USER_PARAM_PREFIX);
                    String portletIdString = encodedName.substring(PARAM_PREFIX.length() + USER_PARAM_PREFIX.length(), encodedName.indexOf('.'));

                    int portletId = Integer.parseInt(portletIdString);
                    String name = encodedName.substring(encodedName.indexOf('.') + 1);
                    String[] values = (String[]) parameterMap.get(encodedName);

                    if (type.equals(CONTAINER_PARAM_PREFIX) && name.equals(TARGET_NAME)) {
                        // We found the special container parameter saying that a certain portlet is the target of a URL
                        requestParameters.setTargetPortletId(portletId);
                        if (ACTION_VALUE.equals(values[0])) {
                            // We found that this must be an action URL
                            requestParameters.setTargetIsAction(true);
                        }
                    }

                    // Add parameter values
                    if (isUser)
                        requestParameters.addUserParameter(portletId, name, values);
                    else
                        requestParameters.addContainerParameter(portletId, name, values);

                } else {
                    // Remember non-encoded parameters
                    if (extraParameters == null)
                        extraParameters = new HashMap();
                    extraParameters.put(encodedName, parameterMap.get(encodedName));
                }
            } catch (Exception ex) {
                // Ignore invalid parameters
                //ex.printStackTrace();
                //continue;
            }
        }

        return requestParameters;
    }

    /**
     * Decode user form parameter names.
     */
    public static String decodeParameterName(String name) {
        int dotIndex = name.indexOf(".");
        if (name.startsWith(PARAM_PREFIX) && dotIndex != -1 && (name.charAt(PARAM_PREFIX.length()) == USER_PARAM_PREFIX_CHAR || name.charAt(PARAM_PREFIX.length()) == ACTION_USER_PARAM_PREFIX_CHAR))
            return name.substring(dotIndex + 1);
        else
            return null;
    }

    /**
     * RequestParameters represents the decoded parameters associated with a request.
     */
    public static class RequestParameters {
        private Map userParametersByPortletId = new HashMap();
        private Map containerParametersByPortletId = new HashMap();
        private int targetPortletId = -1;
        private boolean targetIsAction;

        private Map getParameters(int portletId, boolean isUser) {
            Integer portletIdInteger = new Integer(portletId);
            Map parametersByPortletId = isUser ? userParametersByPortletId : containerParametersByPortletId;
            Map parameters = (Map) parametersByPortletId.get(portletIdInteger);
            if (parameters == null) {
                parameters = new HashMap();
                parametersByPortletId.put(portletIdInteger, parameters);
            }
            return parameters;
        }

        public void addContainerParameter(int portletId, String name, String[] values) {
            getParameters(portletId, false).put(name, values);
        }

        public void addUserParameter(int portletId, String name, String[] values) {
            getParameters(portletId, true).put(name, values);
        }

        private void setTargetPortletId(int portletId) {
            if (portletId < 0)
                throw new IllegalArgumentException("Portlet id: " + portletId);
            if (this.targetPortletId != -1)
                throw new IllegalStateException("setTargetPortletId already called");
            this.targetPortletId = portletId;
        }

        private void setTargetIsAction(boolean targetIsAction) {
            this.targetIsAction = targetIsAction;
        }

        public int getTargetPortletId() {
            return targetPortletId;
        }

        public boolean isTargetAction() {
            return targetIsAction;
        }

        public Map getUserParameters(int portletId) {
            return getParameters(portletId, true);
        }

        public WindowState getWindowState(int portletId) {
            Map containerParameters = getParameters(portletId, false);
            String[] windowStateName = (String[]) containerParameters.get(WINDOW_STATE_NAME);
            return (windowStateName == null) ? null : new WindowState(windowStateName[0]);
        }

        public PortletMode getPortletMode(int portletId) {
            Map containerParameters = getParameters(portletId, false);
            String[] portletModeName = (String[]) containerParameters.get(PORTLET_MODE_NAME);
            return (portletModeName == null) ? null : new PortletMode(portletModeName[0]);
        }
    }


}

