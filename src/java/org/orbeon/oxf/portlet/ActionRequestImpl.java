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
import java.io.*;
import java.util.Collections;
import java.util.Map;

public class ActionRequestImpl extends PortletRequestImpl implements ActionRequest {

    private String characterEncoding;
    private InputStream portletInputStream;
    private BufferedReader portletBufferedReader;

    public ActionRequestImpl(ExternalContext externalContext, PortletConfigImpl portletConfig, int portletId,
                             ExternalContext.Request request, PortletContainer.PortletState portletStatus) {
        super(externalContext, portletConfig, portletId, request, portletStatus);
    }

    public String getCharacterEncoding() {
        return (characterEncoding != null) ? characterEncoding : request.getCharacterEncoding();
    }

    public int getContentLength() {
        return request.getContentLength();
    }

    public String getContentType() {
        return request.getContentType();
    }

    public InputStream getPortletInputStream() throws IOException {
        // Return existing input stream if present
        if (portletInputStream != null)
            return portletInputStream;

        // PLT.11.2.1
        if (portletBufferedReader != null)
            throw new IllegalStateException("getPortletInputStream() cannot be called if getReader() already called");

        portletInputStream = request.getInputStream();

        return portletInputStream;
    }

    public BufferedReader getReader() throws UnsupportedEncodingException, IOException {
        // Return existing reader if present
        if (portletBufferedReader != null)
            return portletBufferedReader;

        // PLT.11.2.1
        if (portletInputStream != null)
            throw new IllegalStateException("getReader() cannot be called if getPortletInputStream() already called");

        // This gets either the caller-set encoding, or the one set by the HTTP client
        String encoding = getCharacterEncoding();
        // However, this can be null if not set by either, BUT Servlet 2.4 says (SRV.4.9) that the
        // default should be iso-8859-1
        if (encoding == null)
            encoding = "iso-8859-1";

        portletBufferedReader = new BufferedReader(new InputStreamReader(request.getInputStream(), encoding));

        return portletBufferedReader;
    }

    public void setCharacterEncoding(String characterEncoding) throws UnsupportedEncodingException {
        this.characterEncoding = characterEncoding;
    }

    public Map getParameterMap() {
        if (readOnlyRequestParameters == null)
            readOnlyRequestParameters = Collections.unmodifiableMap(portletStatus.getActionParameters());
        return readOnlyRequestParameters;
    }
}
