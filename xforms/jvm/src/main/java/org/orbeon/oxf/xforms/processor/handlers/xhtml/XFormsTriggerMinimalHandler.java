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

import org.orbeon.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Minimal (AKA "link") appearance.
 */
public class XFormsTriggerMinimalHandler extends XFormsTriggerHandler {

    public XFormsTriggerMinimalHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, attributes, matched, handlerContext);
    }

    private static final String ENCLOSING_ELEMENT_NAME = "button";

    public void handleControlStart() throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) currentControl();
        final XMLReceiver xmlReceiver = xformsHandlerContext.getController().getOutput();

        final AttributesImpl htmlAnchorAttributes =
            getEmptyNestedControlAttributesMaybeWithId(getEffectiveId(), triggerControl, true);

        htmlAnchorAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "btn-link");

        // Output xxf:* extension attributes
        triggerControl.addExtensionAttributesExceptClassAndAcceptForHandler(htmlAnchorAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI());

        // xhtml:button
        final String xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, ENCLOSING_ELEMENT_NAME);
        xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI(), ENCLOSING_ELEMENT_NAME, spanQName, htmlAnchorAttributes);
        {
            final String labelValue = getTriggerLabel(triggerControl);
            final boolean mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel();
            outputLabelTextIfNotEmpty(labelValue, xhtmlPrefix, mustOutputHTMLFragment, scala.Option.apply(triggerControl.getLocationData()), xmlReceiver);
        }
        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI(), ENCLOSING_ELEMENT_NAME, spanQName);
    }
}
