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

/**
 * This class provides enhanced namespace support for SAX-based handlers.
 */
public class NamespaceSupport3 extends NamespaceSupport2 {

    private boolean mustPushContext = true;

    /**
     * This must be called at the beginning of a SAX startElement() event handler.
     */
    public void startElement() {
        if (mustPushContext)
            pushContext();
        else
            mustPushContext = true;
    }

    /**
     * This must be called at the end of a SAX endElement() event handler.
     */
    public void endElement() {
        popContext();
        mustPushContext = true;
    }

    /**
     * This must be called within a SAX startPrefixMapping() event handler.
     */
    public void startPrefixMapping(String prefix, String uri) {
        if (mustPushContext) {
            pushContext();
            mustPushContext = false;
        }

        declarePrefix(prefix, uri);
    }
}
