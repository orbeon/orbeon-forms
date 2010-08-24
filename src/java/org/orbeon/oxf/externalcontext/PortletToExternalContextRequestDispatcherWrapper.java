/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.PortletException;
import javax.portlet.PortletRequestDispatcher;
import java.io.IOException;

/**
 * Wrap a PortletRequestDispatcher into an ExternalContext.RequestDispatcher.
 */
public class PortletToExternalContextRequestDispatcherWrapper implements ExternalContext.RequestDispatcher {
    private PortletRequestDispatcher dispatcher;

    public PortletToExternalContextRequestDispatcherWrapper(PortletRequestDispatcher _dispatcher) {
        this.dispatcher = _dispatcher;
    }

    public void forward(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
        // NOTE: This forwards to a servlet/JSP, NOT to a portlet
        try {
            dispatcher.forward(new ExternalContextToPortletRenderRequestWrapper(request, true), new ExternalContextToPortletRenderResponseWrapper(response));
        } catch (PortletException e) {
            throw new OXFException(e);
        }
    }

    public void include(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
        // NOTE: This forwards to a servlet/JSP, NOT to a portlet
        try {
            dispatcher.include(new ExternalContextToPortletRenderRequestWrapper(request, true), new ExternalContextToPortletRenderResponseWrapper(response));
        } catch (PortletException e) {
            throw new OXFException(e);
        }
    }

    public boolean isDefaultContext() {
        // TODO
        return false;
    }
}