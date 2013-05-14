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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;

import javax.portlet.*;
import java.io.IOException;
import java.util.Map;

public class ActionResponseImpl extends PortletResponseImpl implements ActionResponse {

    private PortletMode portletMode;
    private WindowState windowState;

    private PortletURLImpl.RequestParameters requestParameters;
    private Map parameters;
    private boolean parametersChanged;
    private String redirectLocation;

    public ActionResponseImpl(int portletId, PipelineContext pipelineContext, ExternalContext externalContext, PortletRequest portletRequest, PortletURLImpl.RequestParameters requestParameters) {
        super(portletId, pipelineContext, externalContext, portletRequest);
        this.requestParameters = requestParameters;
        this.parameters = requestParameters.getUserParameters(portletId);
    }

    public void sendRedirect(String location) throws IOException {
        if (portletMode != null || windowState != null || parametersChanged)
            throw new IllegalStateException("sendRedirect cannot be called after setPortletMode, setWindowState, setRenderParameter or setRenderParameters");
        if (NetUtils.urlHasProtocol(location) || !location.startsWith("/"))
            throw new IllegalArgumentException("A fully qualified URL or a full path URL must be specified: " + location);
        this.redirectLocation = location;
    }

    public void setPortletMode(PortletMode portletMode) throws PortletModeException {
        this.portletMode = portletMode;
    }

    public void setRenderParameter(String key, String value) {
        parametersChanged = true;
        parameters.put(key, new String[] { value });
    }

    public void setRenderParameter(String key, String[] values) {
        parametersChanged = true;
        parameters.put(key, values);
    }

    public void setRenderParameters(Map parameters) {
        if (parameters == null)
            throw new IllegalArgumentException("Render parameters Map cannot be null");
        parametersChanged = true;
        this.parameters = parameters;
    }

    public void setWindowState(WindowState windowState) throws WindowStateException {
        this.windowState = windowState;
    }

    public PortletMode getPortletMode() {
        return portletMode;
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public WindowState getWindowState() {
        return windowState;
    }

    public Map getParameters() {
        return parameters;
    }

    public boolean isParametersChanged() {
        return parametersChanged;
    }
}
