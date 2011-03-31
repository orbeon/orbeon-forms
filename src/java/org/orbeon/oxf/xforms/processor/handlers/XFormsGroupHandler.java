/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;

/**
 * Handle xforms:group.
 */
public abstract class XFormsGroupHandler extends XFormsControlLifecyleHandler {

    public XFormsGroupHandler() {
        super(false, true);
    }

    protected String getLabelClasses(XFormsSingleNodeControl xformsControl) {
        final boolean hasLabel = XFormsControl.hasLabel(containingDocument, getPrefixedId());
        if (hasLabel) {
            final StringBuilder labelClasses = new StringBuilder("xforms-label");

            // Handle relevance on label
            if ((xformsControl == null && !handlerContext.isTemplate()) || (xformsControl != null && !xformsControl.isRelevant())) {
                labelClasses.append(" xforms-disabled");
            }

            // Copy over existing label classes if any
            final String labelClassAttribute = containingDocument.getStaticState().getLabel(getPrefixedId()).element().attributeValue(XFormsConstants.CLASS_QNAME);
            if (labelClassAttribute != null) {
                labelClasses.append(' ');
                labelClasses.append(labelClassAttribute);
            }

            return labelClasses.toString();
        } else {
            return null;
        }
    }

    protected String getLabelValue(XFormsSingleNodeControl xformsControl) {
        if (handlerContext.isTemplate() || xformsControl == null) {
            return null;
        } else {
            return xformsControl.getLabel();
        }
    }
}
