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

import org.orbeon.oxf.xforms.XFormsProperties;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle element for handling AVTs.
 */
public class ElementHandlerXML extends XFormsBaseHandlerXML {
	private String[] refIdAttributeNames;
	
    public ElementHandlerXML() {
        super(false, true);
        
        this.refIdAttributeNames = XFormsProperties.getAdditionalRefIdAttributeNames();
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Start element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        attributes = handleAVTsAndIDs(attributes, refIdAttributeNames);

        contentHandler.startElement(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Close element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
