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

import javax.portlet.ActionRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Map;

public class ActionRequestImpl extends PortletRequestImpl implements ActionRequest {

    public ActionRequestImpl(ExternalContext externalContext, PortletConfigImpl portletConfig, int portletId, ExternalContext.Request request, PortletContainer.PortletState portletStatus) {
        super(externalContext, portletConfig, portletId, request, portletStatus);
    }

    public String getCharacterEncoding() {
        return null;
    }

    public int getContentLength() {
        return 0;
    }

    public String getContentType() {
        return null;
    }

    public InputStream getPortletInputStream() throws IOException {
        return null;
    }

    public BufferedReader getReader() throws UnsupportedEncodingException, IOException {
        return null;
    }

    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
    }

    public Map getParameterMap() {
        if (readOnlyRequestParameters == null)
            readOnlyRequestParameters = Collections.unmodifiableMap(portletStatus.getActionParameters());
        return readOnlyRequestParameters;
    }
}
