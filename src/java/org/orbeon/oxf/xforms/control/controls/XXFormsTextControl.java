/**
 *  Copyright (C) 2008 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;

/**
 * Represents an extension xxforms:text control. This control is used to produce plain text child of xhtml:title, for
 * example. It is based on xforms:output.
 */
public class XXFormsTextControl extends XFormsOutputControl implements XFormsPseudoControl {

    private String forAttribute;

    public XXFormsTextControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        super(containingDocument, parent, element, name, effectiveId);

        // Remember attributes
        this.forAttribute = element.attributeValue("for");
    }

    public String getForAttribute() {
        return forAttribute;
    }

    public String getEffectiveForAttribute() {
        // A kind of hacky way of getting the effective id of the HTML element
        return forAttribute + getEffectiveId().substring(getId().length());
    }
}
