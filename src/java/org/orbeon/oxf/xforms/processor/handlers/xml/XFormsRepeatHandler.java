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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xforms:repeat.
 */
public class XFormsRepeatHandler extends XFormsControlLifecyleHandlerXML {
	
	public XFormsRepeatHandler() {
		super(true, true);
	}
	
	@Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId,
                                      final String effectiveId, XFormsControl control) throws SAXException {
		
		super.handleControlStart(uri, localname, qName, attributes, staticId, effectiveId, control);
		
		final ContentHandler contentHandler = handlerContext.getController().getOutput();
		
		final boolean isTopLevelRepeat = handlerContext.countParentRepeats() == 0;
        final boolean isRepeatSelected = handlerContext.isRepeatSelected() || isTopLevelRepeat;
        
        final int currentIteration = handlerContext.getCurrentIteration();

        final XFormsRepeatControl repeatControl = handlerContext.isTemplate() ? null : (XFormsRepeatControl) containingDocument.getObjectByEffectiveId(effectiveId);
        final boolean isConcreteControl = repeatControl != null;
        
        if (isConcreteControl) {
        	final int currentRepeatIndex = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getIndex();
            final int currentRepeatIterations = (currentIteration == 0 && !isTopLevelRepeat) ? 0 : repeatControl.getSize();
            
            // Unroll repeat
            for (int i = 1; i <= currentRepeatIterations; i++) {
            	// Is the current iteration selected?
                final boolean isCurrentIterationSelected = isRepeatSelected && i == currentRepeatIndex;
                
                reusableAttributes.clear();
                reusableAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "selected", XFormsConstants.XXFORMS_PREFIX + ":selected", ContentHandlerHelper.CDATA, isCurrentIterationSelected?"true":"false");
            	contentHandler.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "itteration", XFormsConstants.XXFORMS_PREFIX + ":itteration", reusableAttributes);
                
                // Apply the content of the body for this iteration
                handlerContext.pushRepeatContext(false, i, isCurrentIterationSelected);
                try {
                    handlerContext.getController().repeatBody();
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(repeatControl.getLocationData(), "unrolling xforms:repeat control", repeatControl.getControlElement()));
                }
                handlerContext.popRepeatContext();
                
                contentHandler.endElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "itteration", XFormsConstants.XXFORMS_PREFIX + ":itteration");
            }
        }
        	
	}
}
