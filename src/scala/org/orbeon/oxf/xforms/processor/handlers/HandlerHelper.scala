/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.oxf.xml.{XMLUtils, DeferredXMLReceiver}
import org.xml.sax.Attributes

object HandlerHelper {
    def withElement[T](prefix: String, uri: String, localName: String, atts: Attributes )(body: ⇒ T)(implicit receiver: DeferredXMLReceiver): T = {
        val qName = XMLUtils.buildQName(prefix, localName)
        receiver.startElement(uri, localName, qName, atts)
        val result = body
        receiver.endElement(uri, localName, qName)
        result
    }

    def element(prefix: String, uri: String, localName: String, atts: Attributes )(implicit receiver: DeferredXMLReceiver) =
        withElement(prefix, uri, localName, atts) {}
}
