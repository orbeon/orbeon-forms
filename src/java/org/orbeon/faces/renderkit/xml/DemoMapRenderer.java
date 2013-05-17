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
 * The original source code by Sun Microsystems has been modifed by
 * Orbeon, Inc.
 */
package org.orbeon.faces.renderkit.xml;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;

public class DemoMapRenderer extends com.sun.faces.renderkit.html_basic.HtmlBasicRenderer {

    public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputStartElement(writer, "map");
        XmlRenderKitUtils.outputAttribute(writer, "name", component.getComponentId());
    }

    public void encodeChildren(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;
    }

    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        if (!XmlRenderKitUtils.checkParams(context, component))
            return;

        ResponseWriter writer = context.getResponseWriter();
        XmlRenderKitUtils.outputEndElement(writer, "map");
    }

    public void decode(FacesContext context, UIComponent component)
            throws IOException {
        XmlRenderKitUtils.checkParams(context, component);
        component.setValid(true);
    }
}
