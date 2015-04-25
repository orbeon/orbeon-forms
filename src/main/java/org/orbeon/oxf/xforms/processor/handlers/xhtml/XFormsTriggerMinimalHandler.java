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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Minimal (AKA "link") appearance.
 */
public class XFormsTriggerMinimalHandler extends XFormsTriggerHandler {

    protected static final String ENCLOSING_ELEMENT_NAME = "a";

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) control;
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, triggerControl, true);

        // TODO: needs f:url-norewrite="true"?
        containerAttributes.addAttribute("", "href", "href", XMLReceiverHelper.CDATA, "#");

        // xhtml:a
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String aQName = XMLUtils.buildQName(xhtmlPrefix, ENCLOSING_ELEMENT_NAME);
        xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, aQName, containerAttributes);
        {
            final String labelValue = getTriggerLabel(triggerControl);
            final boolean mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel();
            outputLabelText(xmlReceiver, triggerControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);
        }
        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, aQName);
    }
}
