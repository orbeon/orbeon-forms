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
import javax.faces.component.UIInput;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;

/**
 * Renderer for a UIInput or UIOutput Text.
 */
public class InputOutputTextRenderer extends com.sun.faces.renderkit.html_basic.TextRenderer {

    public InputOutputTextRenderer() {
    }

    protected void getEndTextToRender(FacesContext context, UIComponent component, String currentValue, StringBuffer buffer) {

        final String elementName = "text";

        if (component instanceof UIInput) {
            // Render input element and class
            buffer.append(XmlRenderKitUtils.getStartElement("input_" + elementName));
            buffer.append(XmlRenderKitUtils.getAttribute("class", (String) component.getAttribute("inputClass")));

            buffer.append(XmlRenderKitUtils.getAttribute("id", component.getClientId(context)));
            buffer.append(XmlRenderKitUtils.getAttribute("value", currentValue));

            // Render HTML attributes
            buffer.append(XmlRenderKitUtils.getHtmlAttributes(context, component));

            buffer.append("/>");
        } else {
            // Render output element
            buffer.append(XmlRenderKitUtils.getStartElement("output_" + elementName));
            buffer.append(XmlRenderKitUtils.getAttribute("class", (String) component.getAttribute("outputClass")));
            buffer.append(">");

            // Render value
            String value = XmlRenderKitUtils.getResource(context, component);
            if (value == null)
                value = ((UIOutput) component).currentValue(context).toString();
            buffer.append(value);

            buffer.append(XmlRenderKitUtils.getEndElement("output_" + elementName));
        }
    }
}
