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
import org.orbeon.oxf.processor.StreamInterceptor;

import javax.portlet.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Locale;

public class RenderResponseImpl extends PortletResponseImpl implements RenderResponse {

    private int portletId;

    private String title;
    private boolean contentTypeSet;
    private String contentType;

    private PrintWriter printWriter;

    private StreamInterceptor streamInterceptor;

    public RenderResponseImpl(int portletId, PipelineContext pipelineContext, ExternalContext externalContext, PortletRequest portletRequest, StreamInterceptor streamInterceptor) {
        super(portletId, pipelineContext, externalContext, portletRequest);
        this.portletId = portletId;
        this.externalContext = externalContext;
        this.streamInterceptor = streamInterceptor;
    }

    public PortletURL createActionURL() {
        return createURL(PortletURLImpl.ACTION_URL);
    }

    public PortletURL createRenderURL() {
        return createURL(PortletURLImpl.USER_URL);
    }

    private PortletURL createURL(int urlType) {
        PortletURL portletURL = new PortletURLImpl(pipelineContext, externalContext, portletId, getURLPrefix(), urlType);
        try {
            // Set current window state and portlet mode. The user will be able to override them.
            portletURL.setWindowState(portletRequest.getWindowState());
            portletURL.setPortletMode(portletRequest.getPortletMode());
        } catch (WindowStateException e) {
            // TODO / CHECK: this may not happen if checked before
            e.printStackTrace();
        } catch (PortletModeException e) {
            // TODO / CHECK: this may not happen if checked before
            e.printStackTrace();
        }
        return portletURL;
    }

    private String getURLPrefix() {
        // The URL will be ready to be included in an HTML fragment, so we inlude the context path
        return externalContext.getRequest().getContextPath() + externalContext.getRequest().getRequestPath();
    }

    public String getNamespace() {
        return externalContext.getResponse().getNamespacePrefix() + "p"+ portletId + "_";
    }

    public void flushBuffer() throws IOException {
        // NIY / FIXME
    }

    public int getBufferSize() {
        // NIY / FIXME
        return 0;
    }

    public String getCharacterEncoding() {
        // NIY / FIXME
        return null;
    }

    public String getContentType() {
        return contentType;
    }

    public Locale getLocale() {
        // NIY / FIXME
        return null;
    }

    public OutputStream getPortletOutputStream() throws IOException {
        if (!contentTypeSet)
            throw new IllegalStateException("Content-type is not set.");
        return streamInterceptor.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        if (!contentTypeSet)
            throw new IllegalStateException("Content-type is not set.");
        if (printWriter == null)
            printWriter = new PrintWriter(streamInterceptor.getWriter());
        return printWriter;
    }

    public boolean isCommitted() {
        // NIY / FIXME
        return false;
    }

    public void reset() {
        // NIY / FIXME
    }

    public void resetBuffer() {
        // NIY / FIXME
    }

    public void setBufferSize(int size) {
        // NIY / FIXME
    }

    public void setContentType(String contentType) {
        this.contentTypeSet = true;
        this.contentType = contentType;
        streamInterceptor.setContentType(contentType);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }


}
