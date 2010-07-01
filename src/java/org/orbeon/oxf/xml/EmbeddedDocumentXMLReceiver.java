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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.pipeline.api.XMLReceiver;

/**
 * Forwards all the SAX events to a content handler, except for startDocument and endDocument.
 */
public class EmbeddedDocumentXMLReceiver extends SimpleForwardingXMLReceiver {
    public EmbeddedDocumentXMLReceiver(XMLReceiver xmlReceiver) {
        super(xmlReceiver);
    }

    @Override
    public void startDocument() {}
    @Override
    public void endDocument() {}
}
