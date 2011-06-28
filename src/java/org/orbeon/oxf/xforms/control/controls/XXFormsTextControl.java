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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Represents an extension xxforms:text control. This control is used to produce plain text child of xhtml:title, for
 * example. It is based on xforms:output.
 */
public class XXFormsTextControl extends XFormsOutputControl implements XFormsPseudoControl {

    private String forAttribute;

    public XXFormsTextControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId, Map<String, Element> state) {
        super(container, parent, element, name, effectiveId, state);

        // Remember attributes
        this.forAttribute = element.attributeValue(XFormsConstants.FOR_QNAME);
    }

    public String getForAttribute() {
        return forAttribute;
    }

    public String getEffectiveForAttribute() {
        // A kind of hacky way of getting the effective id of the HTML element
        return forAttribute + getEffectiveId().substring(getId().length());
    }

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other, AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        assert attributesImpl.getLength() == 0;

        final XXFormsTextControl textControl2 = this;

        // Whether it is necessary to output information about this control
        boolean doOutputElement = false;

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, textControl2.getEffectiveId());

        // The client does not store an HTML representation of the xxforms:text control, so we
        // have to output these attributes.
        {
            // HTML element id
            final String effectiveFor2 = textControl2.getEffectiveForAttribute();
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "for", effectiveFor2, isNewlyVisibleSubtree, false);
        }

        // Output element
        outputValueElement(ch, textControl2, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "text");
    }

    @Override
    public boolean supportFullAjaxUpdates() {
        return false;
    }
}
