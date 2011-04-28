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
package org.orbeon.oxf.xforms.processor.handlers.xml;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:case.
 */
public class XFormsCaseHandler extends XFormsControlLifecyleHandlerXML {
	
	
	public XFormsCaseHandler() {
		super(false, true);
	}

	@Override
	protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
		boolean isVisible;
		isVisible = isCaseVisible(effectiveId);


        super.handleControlStart(uri, localname, qName, attributes, staticId, effectiveId, control);
        handlerContext.pushCaseContext(isVisible);
	}


	@Override
	protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
		handlerContext.popCaseContext();
		
		super.handleControlEnd(uri, localname, qName, attributes, staticId, effectiveId, control);
	}
	
	protected void handleExtraAttributesForControlStart(AttributesImpl reusableAttributes, String effectiveId, XFormsControl control) { 
		reusableAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "visible", XFormsConstants.XXFORMS_PREFIX + ":visible", ContentHandlerHelper.CDATA, isCaseVisible(effectiveId)?"true":"false");
	}
	
	private boolean isCaseVisible(String effectiveId) {
		boolean isVisible;
		// Determine whether this case is visible
        final XFormsCaseControl caseControl = (XFormsCaseControl) containingDocument.getControls().getObjectByEffectiveId(effectiveId);
        if (!handlerContext.isTemplate() && caseControl != null) {
            // This case is visible if it is selected or if the switch is read-only and we display read-only as static
            isVisible = caseControl.isVisible();
        } else {
            isVisible = false;
        }
		return isVisible;
	}
}
