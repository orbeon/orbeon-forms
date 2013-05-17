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

import org.orbeon.faces.components.demo.components.UIArea;

import javax.faces.component.UIComponent;
import javax.faces.webapp.FacesTag;

/**
 * This class is the tag handler that evaluates the <code>area</code> custom
 * tag.
 */
public class AreaTag extends FacesTag {
    protected String onmouseover = null;
    protected String onmouseout = null;
    protected String valueRef = null;

    public AreaTag() {
        super();
    }

    public String getOnmouseover() {
        return onmouseover;
    }

    public void setOnmouseover(String newonmouseover) {
        onmouseover = newonmouseover;
    }

    public String getOnmouseout() {
        return onmouseout;
    }

    public void setOnmouseout(String newonmouseout) {
        onmouseout = newonmouseout;
    }

    public String getValueRef() {
        return valueRef;
    }

    public void setValueRef(String newValueRef) {
        valueRef = newValueRef;
    }

    //
    // Sets the values of the properties of the <code>UIArea</code> component to the values
    // specified in the tag.
    //
    public void overrideProperties(UIComponent component) {
        super.overrideProperties(component);
        UIArea areaComp = (UIArea) component;
        if (areaComp.getValueRef() == null && valueRef != null) {
            areaComp.setValueRef(valueRef);
        }
        if (areaComp.getAttribute("onmouseover") == null) {
            areaComp.setAttribute("onmouseover", getOnmouseover());
        }
        if (areaComp.getAttribute("onmouseout") == null) {
            areaComp.setAttribute("onmouseout", getOnmouseout());
        }
    }

    // Gets the renderer associated with this component
    public String getRendererType() {
        return "Area";
    }

    public String getComponentType() {
        return ("Area");
    }
}