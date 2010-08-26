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
package org.orbeon.oxf.portlet;

import javax.portlet.*;
import java.io.IOException;
import java.io.Writer;

public class OrbeonJSPPortlet extends GenericPortlet {

    public void render(RenderRequest request, RenderResponse response) throws PortletException, IOException {

        final PortletContext portletContext = getPortletContext();
        final PortletRequestDispatcher dispatcher = portletContext.getRequestDispatcher(getPortletConfig().getInitParameter("orbeon.portlet.jsp.path"));

        final Writer writer = response.getWriter();
        writer.write("<xhtml:html xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"\n" +
                "      xmlns:xforms=\"http://www.w3.org/2002/xforms\"\n" +
                "      xmlns:xxforms=\"http://orbeon.org/oxf/xml/xforms\"\n" +
                "      xmlns:ev=\"http://www.w3.org/2001/xml-events\"\n" +
                "      xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "      xmlns:fr=\"http://orbeon.org/oxf/xml/form-runner\">\n" +
                "    <xhtml:head>\n" +
                "        <xhtml:title>XForms Hello</xhtml:title>\n" +
                "        <xforms:model>\n" +
                "            <xforms:instance>\n" +
                "                <first-name xmlns=\"\"/>\n" +
                "            </xforms:instance>\n" +
                "        </xforms:model>\n" +
                "    </xhtml:head>\n" +
                "    <xhtml:body>\n" +
                "        <xhtml:p>\n" +
                "            <xhtml:i>This example is described in details in the <xhtml:a href=\"/doc/intro-tutorial\">Orbeon Forms Tutorial</xhtml:a>.</xhtml:i>\n" +
                "        </xhtml:p>\n" +
                "        <xhtml:p>\n" +
                "            <xforms:input ref=\"/first-name\" incremental=\"true\">\n" +
                "                <xforms:label>Please enter your first name:</xforms:label>\n" +
                "            </xforms:input>\n" +
                "        </xhtml:p>\n" +
                "        <xhtml:p>\n" +
                "            <xforms:output value=\"if (normalize-space(/first-name) = '') then '' else concat('Hello, ', /first-name, '!')\"/>\n" +
                "        </xhtml:p>\n" +
                "    </xhtml:body>\n" +
                "</xhtml:html>");

//        dispatcher.include((PortletRequest) request, (PortletResponse) response);
    }
}
