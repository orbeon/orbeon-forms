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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Renderer for a UIPanel Grid.
 */
public class PanelGridRenderer extends XmlRenderer {

    public PanelGridRenderer() {
    }

    public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputStartElement(writer, getElementName());
        XmlRenderKitUtils.outputAttribute(writer, "class", (String) component.getAttribute("panelClass"));

        // Render HTML attributes
        XmlRenderKitUtils.outputHtmlAttributes(writer, context, component);
        writer.write(">");
    }

    protected String getElementName() {
        return "panel_grid";
    }

    public void encodeChildren(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        ResponseWriter writer = context.getResponseWriter();

        // Find number of columns
        Object columnCountValue = component.getAttribute("columns");
        int columnCount;
        try {
            // Default to 2 columns
            columnCount = (columnCountValue != null) ? Integer.parseInt(columnCountValue.toString()) : 2;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid column number for panel_grid: " + columnCountValue);
        }

        // Output header if present
        UIComponent headerFacet = component.getFacet("header");
        if (headerFacet != null) {
            XmlRenderKitUtils.outputStartElement(writer, "header");
            XmlRenderKitUtils.outputAttribute(writer, "class", (String) component.getAttribute("headerClass"));
            XmlRenderKitUtils.outputAttribute(writer, "colspan", Integer.toString(columnCount));
            writer.write(">");
            XmlRenderKitUtils.encodeRecursive(context, headerFacet);
            writer.write(XmlRenderKitUtils.getEndElement("header"));
        }

        int columnPosition = 0;
        int rowPosition = 0;
        String columnClasses[] = tokenizeClasses((String) component.getAttribute("columnClasses"));
        String rowClasses[] = tokenizeClasses((String) component.getAttribute("rowClasses"));
        // Here we already separate into rows and columns, but you could leave this to XML post-processing
        for (Iterator children = component.getChildren(); children.hasNext();) {
            UIComponent child = (UIComponent) children.next();
            // Beginning of row
            if (columnPosition == 0) {
                // End previous row if needed
                if (rowPosition > 0)
                    XmlRenderKitUtils.outputEndElement(writer, "row");
                // Output start of new row
                XmlRenderKitUtils.outputStartElement(writer, "row");
                if (rowClasses.length > 0)
                    XmlRenderKitUtils.outputAttribute(writer, "class", rowClasses[rowPosition % rowClasses.length]);
                writer.write(">");
            }
            // Start of column
            XmlRenderKitUtils.outputStartElement(writer, "cell");
            if (columnClasses.length > 0)
                XmlRenderKitUtils.outputAttribute(writer, "class", columnClasses[columnPosition % columnClasses.length]);
            writer.write(">");
            // Encode child
            XmlRenderKitUtils.encodeRecursive(context, child);
            // End of column
            XmlRenderKitUtils.outputEndElement(writer, "cell");
            // Update values
            columnPosition++;
            if (columnPosition >= columnCount) {
                columnPosition = 0;
                rowPosition++;
            }
        }
        // Close last row if needed
        if (columnPosition == 0 && rowPosition > 0)
            XmlRenderKitUtils.outputEndElement(writer, "row");

        // Output footer if present
        UIComponent footerFacet = component.getFacet("footer");
        if (footerFacet != null) {
            XmlRenderKitUtils.outputStartElement(writer, "footer");
            XmlRenderKitUtils.outputAttribute(writer, "class", (String) component.getAttribute("footerClass"));
            XmlRenderKitUtils.outputAttribute(writer, "colspan", Integer.toString(columnCount));
            writer.write(">");
            XmlRenderKitUtils.encodeRecursive(context, footerFacet);
            writer.write(XmlRenderKitUtils.getEndElement("footer"));
        }
    }

    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        // Close enclosing element
        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputEndElement(writer, getElementName());
    }

    /**
     * Tokenize a String of comma-separated CSS classes
     */
    private String[] tokenizeClasses(String value) {
        if (value == null)
            return new String[0];

        List list = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(value, ",");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            list.add(token.trim());
        }
        String[] result = new String[list.size()];
        return (String[]) list.toArray(result);
    }
}
