/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

trait XMLReceiverSupport {

    def withElement[T](prefix: String, uri: String, localName: String, atts: Attributes)(body: ⇒ T)(implicit receiver: XMLReceiver): T = {
        val qName = XMLUtils.buildQName(prefix, localName)
        receiver.startElement(uri, localName, qName, atts)
        val result = body
        receiver.endElement(uri, localName, qName)
        result
    }

    def element(prefix: String, uri: String, localName: String, atts: Attributes)(implicit receiver: XMLReceiver) =
        withElement(prefix, uri, localName, atts) {}

    def addAttributes(attributesImpl: AttributesImpl, atts: List[(String, String)]): Unit = {
        atts foreach {
            case (name, value) ⇒
                require(name ne null)
                if (value ne null)
                    attributesImpl.addAttribute("", name, name, "CDATA", value)
        }
    }

    implicit def pairsToAttributes(atts: List[(String, String)]): Attributes = {
        val saxAtts = new AttributesImpl
        addAttributes(saxAtts, atts)
        saxAtts
    }
}
