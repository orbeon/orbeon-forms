/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is XML RenderKit for JSF.
 *
 * The Initial Developer of the Original Code is
 * Orbeon, Inc (info@orbeon.com)
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */
package org.orbeon.faces.renderkit.xml;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;

/**
 * Renderer for a UICommand Button.
 */
public class CommandButtonRenderer extends com.sun.faces.renderkit.html_basic.ButtonRenderer {

    public CommandButtonRenderer() {
    }

    public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        // Render dependent attribute "type" can be "submit" or "reset"
        String type = (String) component.getAttribute("type");
        if (type == null) {
            type = "submit";
            component.setAttribute("type", type);
        }

        // Open element and output class
        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputStartElement(writer, "command_button");
        XmlRenderKitUtils.outputAttribute(writer, "class", (String) component.getAttribute("commandClass"));

        XmlRenderKitUtils.outputAttribute(writer, "type", type);
        XmlRenderKitUtils.outputAttribute(writer, "id", component.getClientId(context));

        // Render HTML attributes
        XmlRenderKitUtils.outputHtmlAttributes(writer, context, component);
        writer.write(">");

        // Use render dependent attributes "label" or "key" / "bundle"
        String label = XmlRenderKitUtils.getResourceOrLabel(context, component);
        if (label != null) {
            XmlRenderKitUtils.outputStartElement(writer, "label");
            writer.write(">");
            writer.write(label);
            XmlRenderKitUtils.outputEndElement(writer, "label");
        }

        // Close element
        XmlRenderKitUtils.outputEndElement(writer, "command_button");
    }
}
