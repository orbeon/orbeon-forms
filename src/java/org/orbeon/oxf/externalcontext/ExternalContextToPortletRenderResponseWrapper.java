/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.RenderResponse;
import javax.portlet.PortletURL;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Wrap an ExternalContext.Request into a RenderResponse.
 */
public class ExternalContextToPortletRenderResponseWrapper implements RenderResponse {
    private ExternalContext.Response response;

    public ExternalContextToPortletRenderResponseWrapper(ExternalContext.Response response) {
        this.response = response;
    }

    public PortletURL createActionURL() {
        return null;//TODO
    }

    public PortletURL createRenderURL() {
        return null;//TODO
    }

    public void flushBuffer() throws IOException {
        //TODO
    }

    public int getBufferSize() {
        return 0;//TODO
    }

    public String getCharacterEncoding() {
        return response.getCharacterEncoding();
    }

    public String getContentType() {
        return null;//TODO
    }

    public Locale getLocale() {
        return null;//TODO
    }

    public String getNamespace() {
        return null;//TODO
    }

    public OutputStream getPortletOutputStream() throws IOException {
        return response.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        return response.getWriter();
    }

    public boolean isCommitted() {
        return response.isCommitted();
    }

    public void reset() {
        response.reset();
    }

    public void resetBuffer() {
        //TODO
    }

    public void setBufferSize(int i) {
        //TODO
    }

    public void setContentType(String clazz) {
        response.setContentType(clazz);
    }

    public void setTitle(String clazz) {
        //TODO
    }

    public void addProperty(String clazz, String clazz1) {
    }

    public String encodeURL(String clazz) {
        return null;//TODO
    }

    public void setProperty(String clazz, String clazz1) {
        //TODO
    }
}
