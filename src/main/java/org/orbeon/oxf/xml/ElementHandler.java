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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Base class for all element handlers.
 */
public abstract class ElementHandler {

    private Object context;

    /**
     * Override this to detect that the element is being initialized.
     *
     *
     * @param uri           element namespace URI
     * @param localname     element local name
     * @param qName         element qualified name
     * @param attributes    element attributes
     * @param matched       object returned by the matcher
     * @throws SAXException
     */
    public void init(String uri, String localname, String qName, Attributes attributes, Object matched) throws SAXException {
    }

    /**
     * Override this to detect that the element has started.
     *
     * @param uri           element namespace URI
     * @param localname     element local name
     * @param qName         element qualified name
     * @param attributes    element attributes
     * @throws SAXException
     */
    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
    }

    /**
     * Override this to detect that the element has ended.
     *
     * @param uri           element namespace URI
     * @param localname     element local name
     * @param qName         element qualified name
     * @throws SAXException
     */
    public void end(String uri, String localname, String qName) throws SAXException {
    }

    /**
     * Whether the body of the handled element may be repeated.
     *
     * @return true iif the body may be repeated
     */
    public abstract boolean isRepeating();

    /**
     * Whether the body of the handled element must be processed.
     *
     * @return true iif the body of the handled element must be processed
     */
    public abstract boolean isForwarding();

    /**
     * Set a context object for this handler.
     *
     * @param context context object
     */
    public void setContext(Object context) {
        this.context = context;
    }

    /**
     * Return a context object for this handler.
     *
     * @return context object
     */
    public Object getContext() {
        return context;
    }
}
