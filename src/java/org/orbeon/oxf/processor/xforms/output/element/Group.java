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

import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class Group extends XFormsElement {

    private boolean isFirstGroup;

    public void start(XFormsElementContext context, String uri, String localname, String qname, Attributes attributes) throws SAXException {
        isFirstGroup = context.getParentElement(0) == null;
        super.start(context, uri, localname, qname, attributes);
    }

    public void end(XFormsElementContext context, String uri, String localname, String qname) throws SAXException {
        if (isFirstGroup) {
            // Encode instance in a string and put in hidden field
            final String instanceString = XFormsUtils.instanceToString(context.getInstance());
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, "name",
                    Constants.XXFORMS_PREFIX + ":name", "CDATA", "$instance");
            attributes.addAttribute(Constants.XXFORMS_NAMESPACE_URI, "value",
                    Constants.XXFORMS_PREFIX + ":value", "CDATA", instanceString);
            String elementLocalName = "hidden";
            String elementQName = Constants.XXFORMS_PREFIX + ":" + elementLocalName;
            context.getContentHandler().startElement(Constants.XXFORMS_NAMESPACE_URI,
                    elementLocalName, elementQName, attributes);
            context.getContentHandler().endElement(Constants.XXFORMS_NAMESPACE_URI,
                    elementLocalName, elementQName);
        }

        // Close form
        super.end(context, uri, localname, qname);
    }
}
