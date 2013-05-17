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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.util.Iterator;

/**
 * <p>Render the individual {@link PaneComponent} and its children, but
 * <strong>only</strong> if this {@link PaneComponent} is currently
 * selected.  Otherwise, no output at all is sent.</p>
 *
 * <p><strong>IMPLEMENTATION NOTE</strong> - Because of the fact that we
 * want standard JSP text (not nested inside Faces components) to be usable
 * on a pane, this Renderer needs to know whether it is being used in a JSP
 * environment (where the rendering of the child components writes to the
 * local value of our Pane component) or not (where we must do it ourselves).
 * This is resolved by having the <code>Pane_Tab</code> tag set a
 * render dependent attribute named "demo.renderer.TabRenderer.JSP" when it
 * creates the corresponding component, so that we can tell what is going on.
 * </p>
 */
public class TabRenderer extends BaseRenderer {

    private static Log log = LogFactory.getLog(TabRenderer.class);


    public void decode(FacesContext context, UIComponent component)
            throws IOException {
    }


    public void encodeBegin(FacesContext context, UIComponent component)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("encodeBegin(" + component.getComponentId() + ")");
        }

    }


    public void encodeChildren(FacesContext context, UIComponent component)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("encodeChildren(" + component.getComponentId() + ")");
        }

    }


    public void encodeEnd(FacesContext context, UIComponent component)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("encodeEnd(" + component.getComponentId() + ")");
        }

        // Render our children only -- our parent has rendered ourself
        Iterator kids = component.getChildren();
        while (kids.hasNext()) {
            UIComponent kid = (UIComponent) kids.next();
            encodeRecursive(context, kid);
        }

    }


    private void encodeRecursive(FacesContext context, UIComponent component)
            throws IOException {

        component.encodeBegin(context);
        if (component.getRendersChildren()) {
            component.encodeChildren(context);
        } else {
            Iterator kids = component.getChildren();
            while (kids.hasNext()) {
                UIComponent kid = (UIComponent) kids.next();
                encodeRecursive(context, kid);
            }
        }
        component.encodeEnd(context);

    }
}
