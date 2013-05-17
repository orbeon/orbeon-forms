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
package org.orbeon.faces.components.demo.taglib;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.orbeon.faces.components.demo.components.PaneComponent;

import javax.faces.component.UIComponent;
import javax.faces.webapp.FacesTag;

/**
 * This class creates a <code>PaneComponent</code> instance
 * that represents a tab button control on the tab pane.
 */
public class PaneTabLabelTag extends FacesTag {

    private static Log log = LogFactory.getLog(PaneTabLabelTag.class);

    protected String label = null;
    protected String image = null;
    protected String commandName = null;

    public String getLabel() {
        return label;
    }

    public void setLabel(String newLabel) {
        label = newLabel;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String newImage) {
        image = newImage;
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String newCommandName) {
        commandName = newCommandName;
    }

    public String getComponentType() {
        return ("Pane");
    }

    public String getRendererType() {
        return ("TabLabel");
    }

    public void release() {
        super.release();
        this.label = null;
        this.image = null;
        this.commandName = null;
    }

    protected void overrideProperties(UIComponent component) {

        // Standard override processing
        super.overrideProperties(component);

        PaneComponent pane = (PaneComponent) component;

        if (null == pane.getAttribute("label")) {
            pane.setAttribute("label", getLabel());
        }
        if (null == pane.getAttribute("image")) {
            pane.setAttribute("image", getImage());
        }
        if (null == pane.getAttribute("commandName")) {
            pane.setAttribute("commandName", getCommandName());
        }
    }
}
