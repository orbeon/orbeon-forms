/*
 * Copyright 2002, 2003 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */
/*
 * The original source code by Sun Microsystems has been modifed by Orbeon,
 * Inc.
 */
package org.orbeon.faces.components.demo.renderkit;

import org.orbeon.faces.components.demo.components.UIArea;
import org.orbeon.faces.components.demo.model.ImageArea;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;

/**
 * This class converts the internal representation of a <code>UIArea</code>
 * component into the output stream associated with the response to a particular
 * request.
 */
public class AreaRenderer extends BaseRenderer {

    public AreaRenderer() {
        super();
    }

    // Returns true if this renderer can render the specified component
    // type.  This renderer supports only the <code>UIArea</code> component,
    // and so the <code>componentType</code> argument must be
    // <code>UIArea.TYPE</code>
    public boolean supportsComponentType(String componentType) {
        if (componentType == null) {
            throw new NullPointerException();
        }
        return (componentType.equals(UIArea.TYPE));
    }

// Overrides the default behavior and takes no action.
    public void encodeBegin(FacesContext context, UIComponent component)
            throws IOException {
        if (context == null || component == null) {
            throw new NullPointerException();
        }
    }
// Overrides the default behavior and takes no action.
    public void encodeChildren(FacesContext context, UIComponent component)
            throws IOException {
        if (context == null || component == null) {
            throw new NullPointerException();
        }
    }

// Renders the <code>area</code> tags
    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {

        if (context == null) {
            throw new NullPointerException();
        }

        UIArea uiArea = (UIArea) component;
        ImageArea ia = (ImageArea)
                ((Util.getValueBinding(uiArea.getValueRef())).getValue(context));
        if (ia == null) {
            return;
        }
        String contextPath = context.getExternalContext().getRequestContextPath();
        String imagePath = null;

        ResponseWriter writer = context.getResponseWriter();
        writer.write("<jsf:area shape=\"");
        writer.write(ia.getShape());
        writer.write("\"");
        writer.write(" coords=\"");
        writer.write(ia.getCoords());
        writer.write("\" selectedarea=\"");
        writer.write(component.getComponentId());
        writer.write("\"");
//        writer.write("\" onclick=\"document.forms[0].selectedArea.value='");
//        writer.write(component.getComponentId());
//        writer.write("'; document.forms[0].submit()\"");
        writer.write(" onmouseover=\"");
        writer.write("document.forms[0].mapImage.src='");
        imagePath = (String) component.getAttribute("onmouseover");
        if (imagePath.startsWith("/")) {
            writer.write(contextPath);
        }
        writer.write(imagePath);
        writer.write("';\"");
        writer.write(" onmouseout=\"");
        writer.write("document.forms[0].mapImage.src='");
        imagePath = (String) component.getAttribute("onmouseout");
        if (imagePath.startsWith("/")) {
            writer.write(contextPath);
        }
        writer.write(imagePath);
        writer.write("';\"");
        writer.write(" alt=\"");
        writer.write(ia.getAlt());
        writer.write("\"");
        writer.write("/>");

    }

    public void decode(FacesContext context, UIComponent component)
            throws IOException {
        if (context == null || component == null) {
            throw new NullPointerException();
        }
        component.setValid(true);
    }
}