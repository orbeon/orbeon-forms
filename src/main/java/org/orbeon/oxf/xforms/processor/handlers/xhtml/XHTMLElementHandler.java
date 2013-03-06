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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xhtml:* for handling AVTs as well as rewriting @id and @for.
 */
public class XHTMLElementHandler extends XFormsBaseHandlerXHTML {
    /**
	 * 
	 */
	private static final String[] REF_ID_ATTRIBUTE_NAMES = new String[] { "for" };

	public XHTMLElementHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        // Start xhtml:* element
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        
        attributes = handleAVTsAndIDs(attributes, REF_ID_ATTRIBUTE_NAMES);

        contentHandler.startElement(uri, localname, qName, attributes);
    }

	
    public void end(String uri, String localname, String qName) throws SAXException {
        // Close xhtml:*
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(uri, localname, qName);
    }
}
