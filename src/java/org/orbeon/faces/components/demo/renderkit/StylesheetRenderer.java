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

import javax.faces.component.UIComponent;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;


/**
 * <p>Render a stylesheet link for the value of our component's
 * <code>path</code> attribute, prefixed by the context path of this
 * web application.</p>
 */
public class StylesheetRenderer extends BaseRenderer {


    public boolean supportsComponentType(UIComponent component) {
        return (component instanceof UIOutput);
    }

    public void decode(FacesContext context, UIComponent component)
            throws IOException {

        component.setValid(true);

    }


    public void encodeBegin(FacesContext context, UIComponent component)
            throws IOException {
        ;
    }


    public void encodeChildren(FacesContext context, UIComponent component)
            throws IOException {
        ;
    }


    /**
     * <p>Render a relative HTML <code>&lt;link&gt;</code> element for a
     * <code>text/css</code> stylesheet at the specified context-relative
     * path.</p>
     *
     * @param context FacesContext for the request we are processing
     * @param component UIComponent to be rendered
     *
     * @exception IOException if an input/output error occurs while rendering
     * @exception NullPointerException if <code>context</code>
     *  or <code>component</code> is null
     */
    public void encodeEnd(FacesContext context, UIComponent component)
            throws IOException {

        if ((context == null) || (component == null)) {
            throw new NullPointerException();
        }

        ResponseWriter writer = context.getResponseWriter();
        String contextPath = context.getExternalContext().getRequestContextPath();
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"");

        writer.write(contextPath);
        writer.write((String) component.getAttribute("path"));
        writer.write("\">");

    }
}
