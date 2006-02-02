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
package org.orbeon.oxf.xml.dom4j;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXWriter;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class LocationSAXWriter extends SAXWriter {

    protected Element curElement;
    protected boolean locatorSet = false;
    protected Locator loc = new Locator() {

        public String getPublicId() {
            LocationData locationData = getLocationData();
            return locationData == null ? null : locationData.getPublicID();
        }

        public String getSystemId() {
            LocationData locationData = getLocationData();
            return locationData == null ? null : locationData.getSystemID();
        }

        public int getLineNumber() {
            LocationData locationData = getLocationData();
            return locationData == null ? -1 : locationData.getLine();
        }

        public int getColumnNumber() {
            LocationData locationData = getLocationData();
            return locationData == null ? -1 : locationData.getCol();
        }

        private LocationData getLocationData() {
            if (curElement != null) {
                Object data = curElement.getData();
                return data != null && data instanceof LocationData
                    ? (LocationData) curElement.getData() : null;
            } else {
                return null;
            }
        }
    };

    protected void documentLocator(Document document) throws SAXException {
        if (!locatorSet) {
            locatorSet = true;
            getContentHandler().setDocumentLocator(loc);
        }
    }

    public void write(Element element) throws SAXException {
        documentLocator(null);
        super.write(element);
    }

    public void write(Node node) throws SAXException {
        documentLocator(null);
        super.write(node);
    }

    protected void startElement(Element element, AttributesImpl namespaceAttributes) throws SAXException {
        curElement = element;
        super.startElement(element, namespaceAttributes);
    }
}
