/**
 * Copyright (C) 2014 Orbeon, Inc.
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
import org.orbeon.scaxon.SAXEvents._

// Receiver which produces a subtree rooted at the first element matching the predicate provided.
class FilterReceiver(receiver: XMLReceiver, matches: List[StartElement] => Boolean)
    extends ForwardingXMLReceiver(receiver) {

  super.setForward(false)

  private val namespaceContext = new NamespaceContext
  private var stack: List[StartElement] = Nil
  private var level = 0
  private var matchLevel = -1
  private var done = false

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    if (! done) {

      level += 1
      stack ::= StartElement(uri, localname, qName, attributes)

      namespaceContext.startElement()

      if (matchLevel < 0 && matches(stack)) {
        matchLevel = level
        super.setForward(true)
        super.startDocument()
        for ((prefix, uri) <- namespaceContext.current.mappings)
          super.startPrefixMapping(prefix, uri)
      }
    }

    super.startElement(uri, localname, qName, attributes)
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {

    super.endElement(uri, localname, qName)

    if (! done) {

      if (matchLevel == level) {
        matchLevel = -1

        for ((prefix, _) <- namespaceContext.current.mappings)
          super.endPrefixMapping(prefix)

        super.endDocument()
        super.setForward(false)
        done = true
      }

      namespaceContext.endElement()
      stack = stack.tail
      level -= 1
    }
  }

  override def startPrefixMapping(prefix: String, uri: String): Unit = {
    super.startPrefixMapping(prefix, uri)

    if (! done)
      namespaceContext.startPrefixMapping(prefix, uri)
  }
}

