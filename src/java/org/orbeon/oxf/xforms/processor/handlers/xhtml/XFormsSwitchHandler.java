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

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:switch.
 */
public class XFormsSwitchHandler extends XFormsControlLifecyleHandler {

    public XFormsSwitchHandler() {
        super(false, true);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        if (!handlerContext.isSpanHTMLLayout()) {
            // Start xhtml:span
            final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, control, true);
            handlerContext.getController().getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(), containerAttributes);
        }
    }

    @Override
    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        if (!handlerContext.isSpanHTMLLayout()) {
            // Close xhtml:span
            handlerContext.getController().getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
        }
    }
}
