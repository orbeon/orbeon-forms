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

    public String uri;
    public String localname;
    public String qName;
    public Attributes attributes;
    public Object matched;
    private Object handlerContext;

    public String getUri() {
        return uri;
    }

    public String getLocalname() {
        return localname;
    }

    public String getqName() {
        return qName;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public Object getMatched() {
        return matched;
    }

    public ElementHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        this.uri            = uri;
        this.localname      = localname;
        this.qName          = qName;
        this.attributes     = attributes;
        this.matched        = matched;
        this.handlerContext = handlerContext;
    }

    // Override this to detect that the element has started.
    public void start() throws SAXException {
    }

    // Override this to detect that the element has ended.
    public void end() throws SAXException {
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
     * Return a context object for this handler.
     *
     * @return context object
     */
    public Object getContext() {
        return handlerContext;
    }
}
