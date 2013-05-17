/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public abstract class ElementHandlerNew extends ForwardingContentHandler {

    private Object context;

    /**
     * Override this to detect that the element has started.
     *
     * @param uri
     * @param localname
     * @param qName
     * @param attributes
     * @throws SAXException
     */
    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
    }

    /**
     * Override this to detect that the element has ended.
     *
     * @param uri
     * @param localname
     * @param qName
     * @throws SAXException
     */
    public void end(String uri, String localname, String qName) throws SAXException {
    }

    public abstract boolean isRepeating();

    public abstract boolean isForwarding();

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
