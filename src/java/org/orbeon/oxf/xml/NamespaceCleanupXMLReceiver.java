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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This class takes care of cleaning-up namespaces.
 *
 * Some tools generate namespace "un-declarations" or the form xmlns:abc="". While this is needed to
 * keep the XML infoset correct, it is illegal to generate such declarations in XML 1.0 (but it is
 * legal in XML 1.1).
 */
public class NamespaceCleanupXMLReceiver extends ForwardingXMLReceiver {

    private boolean filterNamespaceEvents;

    public NamespaceCleanupXMLReceiver(XMLReceiver xmlReceiver, boolean serializeXML11) {
        super(xmlReceiver);
        this.filterNamespaceEvents = !serializeXML11;
    }

    public NamespaceCleanupXMLReceiver(ContentHandler contentHandler, boolean serializeXML11) {
        super(contentHandler);
        this.filterNamespaceEvents = !serializeXML11;
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (!filterNamespaceEvents || !(prefix.length() > 0 && uri.equals("")))
            super.startPrefixMapping(prefix, uri);
    }
}
