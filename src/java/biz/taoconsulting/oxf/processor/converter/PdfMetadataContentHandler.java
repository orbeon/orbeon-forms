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
package biz.taoconsulting.oxf.processor.converter;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.xml.sax.SAXException;

/**
 * created 26-Apr-2005 11:33:49
 *
 * @author stw file PdfMetadataContentHandler.java We just need to forward all
 *         sax events with the exception of start and end document. All other
 *         events are handled by the superclass
 */
public class PdfMetadataContentHandler extends ForwardingXMLReceiver {

    public PdfMetadataContentHandler(XMLReceiver xmlReceiver) {
        super(xmlReceiver);
    }

    // The superclass does all we need, we just need to strip out
    // the start & End document events since we add the content into
    // the middle of an existing fle
    public void endDocument() throws SAXException {
        // We don't take action and don't call the super event
    }

    public void startDocument() throws SAXException {
        // We don't take action and don't call the super event
    }
}
