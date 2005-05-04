/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xforms.output.element;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;

public class Repeat extends XFormsElement {

    private List nodeset;
    private boolean firstChild = true;
    private int lastIndex;
    private int currentIndex;
    private String repeatId;

    public boolean repeatChildren() {
        return true;
    }

    public boolean nextChildren(XFormsElementContext context) throws SAXException {

        if (firstChild) {
            context.startRepeatId(repeatId);
        } else {
            // End group
            super.end(context, XFormsConstants.XFORMS_NAMESPACE_URI, "group",
                    XFormsConstants.XFORMS_PREFIX + ":group");
        }

        if (currentIndex <= lastIndex) {
            // Update context
            context.setRepeatIdIndex(repeatId, currentIndex);

            // Start group
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "position",
                    XFormsConstants.XXFORMS_PREFIX + ":position", "CDATA", Integer.toString(currentIndex));
            super.start(context, XFormsConstants.XFORMS_NAMESPACE_URI, "group",
                    XFormsConstants.XFORMS_PREFIX + ":group", attributes);

            // Prepare for next one
            currentIndex++;
            firstChild = false;
            return true;
        } else {
            context.endRepeatId(repeatId);
            return false;
        }
    }

    public void start(XFormsElementContext context, String uri, String localname, String qname, Attributes attributes) throws SAXException {

        repeatId = attributes.getValue("id");

        // Figure out start index
        nodeset = context.getCurrentNodeset();
        String startindexAttribute = attributes.getValue("startindex");
        currentIndex = startindexAttribute == null ? 1 : Integer.parseInt(startindexAttribute);

        // Figure out last index
        String numberAttribute = attributes.getValue("number");
        lastIndex = numberAttribute == null ? nodeset.size()
                : Math.min(nodeset.size(), currentIndex + Integer.parseInt(numberAttribute) - 1);

        context.getContentHandler().startElement(uri, localname, qname, attributes);
    }
}
