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

import javax.faces.component.UICommand;
import javax.faces.component.UIComponent;
import javax.faces.component.UIParameter;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Renderer for a UICommand Hyperlink.
 */
public class CommandHyperlinkRenderer extends com.sun.faces.renderkit.html_basic.HyperlinkRenderer {

    public CommandHyperlinkRenderer() {
    }

    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        // Open element
        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputStartElement(writer, "command_hyperlink");
        XmlRenderKitUtils.outputAttribute(writer, "class", (String) component.getAttribute("commandClass"));

        String href = (String) component.getAttribute("href");
        List parameters = XmlRenderKitUtils.findParameters(context, component);
        ExternalContext externalContext = context.getExternalContext();
        String path = null;
        String originalPath = null;
        if (href != null) {
            // This is the case where the user specified an href attribute
            originalPath = externalContext.encodeURL(href);
            // Do not prepend externalContext.getRequestContextPath() as we do URL rewriting later
            if (href.startsWith("/faces"))
                path = externalContext.encodeURL(href);
            else
                path = originalPath; 
            // Add parameters
            StringBuffer url = new StringBuffer();
            if (parameters.size() > 0) {
                url.append((path.indexOf('?') == -1) ? '?' : '&');
                for (Iterator i = parameters.iterator(); i.hasNext();) {
                    UIParameter parameter = (UIParameter) i.next();
                    url.append(parameter.getName());
                    url.append('=');
                    url.append(externalContext.encodeURL((String) parameter.currentValue(context)));
                }
            }
            XmlRenderKitUtils.outputAttribute(writer, "href", path + url.toString());
            XmlRenderKitUtils.outputAttribute(writer, "original-href", originalPath + url.toString());
        } else {
            XmlRenderKitUtils.outputAttribute(writer, "id", component.getClientId(context));
            XmlRenderKitUtils.outputAttribute(writer, "command-name", ((UICommand) component).getCommandName());
        }
        writer.write(">");
        // Output path if needed
        if (path != null) {
            XmlRenderKitUtils.outputStartElement(writer, "path");
            writer.write(">");
            writer.write(path);
            XmlRenderKitUtils.outputEndElement(writer, "path");
        }
        if (originalPath != null) {
            XmlRenderKitUtils.outputStartElement(writer, "original-path");
            writer.write(">");
            writer.write(originalPath);
            XmlRenderKitUtils.outputEndElement(writer, "original-path");
        }
        // Output parameters
        XmlRenderKitUtils.outputStartElement(writer, "parameters");
        writer.write(">");
        for (Iterator i = parameters.iterator(); i.hasNext();) {
            UIParameter parameter = (UIParameter) i.next();
            XmlRenderKitUtils.outputStartElement(writer, "parameter");
            writer.write(">");
            XmlRenderKitUtils.outputStartElement(writer, "name");
            writer.write(">");
            writer.write(parameter.getName());
            XmlRenderKitUtils.outputEndElement(writer, "name");
            XmlRenderKitUtils.outputStartElement(writer, "value");
            writer.write(">");
            writer.write(externalContext.encodeURL((String) parameter.currentValue(context)));
            XmlRenderKitUtils.outputEndElement(writer, "value");
            XmlRenderKitUtils.outputEndElement(writer, "parameter");
        }
        XmlRenderKitUtils.outputEndElement(writer, "parameters");
        // Output label element
        String label = XmlRenderKitUtils.getResourceOrLabel(context, component);
        if (label != null) {
            XmlRenderKitUtils.outputStartElement(writer, "label");
            writer.write(">");
            writer.write(label);
            XmlRenderKitUtils.outputEndElement(writer, "label");
        }

        // Close element
        XmlRenderKitUtils.outputEndElement(writer, "command_hyperlink");
    }
}
