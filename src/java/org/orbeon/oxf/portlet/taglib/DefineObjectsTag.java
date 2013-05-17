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

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;

public class DefineObjectsTag implements Tag {

    private PageContext pageContext;
    private Tag parent;

    public int doEndTag() throws JspException {

        Object request = pageContext.getRequest().getAttribute(PortletRequestDispatcherImpl.REQUEST_ATTRIBUTE);
        Object response = pageContext.getRequest().getAttribute(PortletRequestDispatcherImpl.RESPONSE_ATTRIBUTE);
        Object config = pageContext.getRequest().getAttribute(PortletRequestDispatcherImpl.CONFIG_ATTRIBUTE);

        pageContext.setAttribute("renderRequest", request);
        pageContext.setAttribute("renderResponse", response);
        pageContext.setAttribute("portletConfig", config);

        return Tag.EVAL_PAGE;
    }

    public int doStartTag() throws JspException {
        return Tag.SKIP_BODY;
    }

    public Tag getParent() {
        return parent;
    }

    public void release() {
    }

    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    public void setParent(Tag tag) {
        this.parent = tag;
    }
}
