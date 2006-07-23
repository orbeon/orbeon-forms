/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

/**
 * Represents an xforms:repeat pseudo-control.
 */
public class XFormsRepeatControl extends XFormsControl {
    private int startIndex;
    public XFormsRepeatControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);

        // Store initial repeat index information
        final String startIndexString = element.attributeValue("startindex");
        this.startIndex = (startIndexString != null) ? Integer.parseInt(startIndexString) : 1;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public String getRepeatId() {
        return getOriginalId();
    }

    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        final XFormsRepeatControl other = (XFormsRepeatControl) obj;
        return this.startIndex == other.startIndex;
    }
}
