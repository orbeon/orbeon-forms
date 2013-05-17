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
package org.orbeon.oxf.portlet.taglib;

import org.orbeon.oxf.portlet.PortletRequestDispatcherImpl;

import javax.portlet.*;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;

public abstract class URLBaseTag extends TagSupport {

    private boolean render;

    private String windowState;
    private String portletMode;
    private String var;
    private String secure;

    protected URLBaseTag(boolean render) {
        this.render = render;
    }

    public int doEndTag() throws JspException {

        RenderResponse response = (RenderResponse) pageContext.getRequest().getAttribute(PortletRequestDispatcherImpl.RESPONSE_ATTRIBUTE);

        PortletURL url = render ? response.createRenderURL() : response.createActionURL();

        try {
            if (windowState != null)
                url.setWindowState(new WindowState(windowState));
            if (portletMode != null)
                url.setPortletMode(new PortletMode(portletMode));
            if (secure != null)
                url.setSecure(new Boolean(secure).booleanValue());
        } catch (PortletException e) {
            // As per the spec
            throw new JspException(e);
        }

        if (var == null) {
            try {
                pageContext.getOut().print(url.toString());
            } catch (IOException e) {
                throw new JspException(e);
            }
        } else {
            pageContext.setAttribute(var, url.toString());
        }

        return Tag.EVAL_PAGE;
    }

    public String getPortletMode() {
        return portletMode;
    }

    public void setPortletMode(String portletMode) {
        this.portletMode = portletMode;
    }

    public String getSecure() {
        return secure;
    }

    public void setSecure(String secure) {
        this.secure = secure;
    }

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public String getWindowState() {
        return windowState;
    }

    public void setWindowState(String windowState) {
        this.windowState = windowState;
    }
}
