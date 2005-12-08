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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.PortletContext;

/**
 *
 */
public class PortletContextExternalContext extends PortletWebAppExternalContext implements ExternalContext {

    public PortletContextExternalContext(PortletContext portletContext) {
        super(portletContext);
    }

    public Object getNativeRequest() {
        throw new UnsupportedOperationException();
    }

    public Object getNativeResponse() {
        throw new UnsupportedOperationException();
    }

    public Object getNativeSession(boolean flag) {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        throw new UnsupportedOperationException();
    }

    public Request getRequest() {
        throw new UnsupportedOperationException();
    }

    public Response getResponse() {
        throw new UnsupportedOperationException();
    }

    public Session getSession(boolean create) {
        throw new UnsupportedOperationException();
    }

    public String getStartLoggerString() {
        return "";
    }

    public String getEndLoggerString() {
        return "";
    }
}
