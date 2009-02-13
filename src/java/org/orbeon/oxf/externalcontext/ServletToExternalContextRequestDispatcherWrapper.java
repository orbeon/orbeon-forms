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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Wrap an RequestDispatcher into an ExternalContext.RequestDispatcher.
 */
public class ServletToExternalContextRequestDispatcherWrapper implements ExternalContext.RequestDispatcher {

    private javax.servlet.RequestDispatcher dispatcher;
    private boolean isDefaultContext;

    public ServletToExternalContextRequestDispatcherWrapper(javax.servlet.RequestDispatcher dispatcher, boolean isDefaultContext) {
        this.dispatcher = dispatcher;
        this.isDefaultContext = isDefaultContext;
    }

    public void forward(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
        try {
            dispatcher.forward(new ExternalContextToHttpServletRequestWrapper(request, true), new ExternalContextToHttpServletResponseWrapper(response));
        } catch (ServletException e) {
            throw new OXFException(e);
        }
    }

    public void include(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
        try {
            dispatcher.include(new ExternalContextToHttpServletRequestWrapper(request, false), new ExternalContextToHttpServletResponseWrapper(response));
        } catch (ServletException e) {
            throw new OXFException(e);
        }
    }

    public boolean isDefaultContext() {
        return isDefaultContext;
    }
}